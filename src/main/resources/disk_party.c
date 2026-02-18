#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <pthread.h>
#include <string.h>
#include <time.h>
#include <sys/time.h>
#include <errno.h>

#define BUFFER_SIZE (1024 * 1024) // 1MB
#define ALIGNMENT 4096

typedef struct {
    char path[1024];
    long long bytes_to_write;
    int duration;
    long long *total_written;
    pthread_mutex_t *mutex;
    int thread_id;
} thread_args;

void *write_stress(void *arg) {
    thread_args *args = (thread_args *)arg;
    
    // O_DIRECT requires aligned memory and IO size on Linux
    int flags = O_WRONLY | O_CREAT | O_TRUNC | O_SYNC;
#ifdef O_DIRECT
    flags |= O_DIRECT;
#endif

    int fd = open(args->path, flags, 0644);
    if (fd < 0) {
        fprintf(stderr, "Thread %d: Failed to open file '%s': %s\n", args->thread_id, args->path, strerror(errno));
        return NULL;
    }

#ifdef F_NOCACHE
    // Darwin specific: bypass HFS+/APFS cache
    if (fcntl(fd, F_NOCACHE, 1) == -1) {
        fprintf(stderr, "Thread %d: Failed to set F_NOCACHE: %s\n", args->thread_id, strerror(errno));
    }
#endif

    char *buf;
    if (posix_memalign((void **)&buf, ALIGNMENT, BUFFER_SIZE) != 0) {
        fprintf(stderr, "Thread %d: Failed to allocate aligned memory\n", args->thread_id);
        close(fd);
        return NULL;
    }
    memset(buf, 0x42, BUFFER_SIZE);

    struct timeval start, now;
    gettimeofday(&start, NULL);
    long long written = 0;

    while (1) {
        gettimeofday(&now, NULL);
        double elapsed = (now.tv_sec - start.tv_sec) + (now.tv_usec - start.tv_usec) / 1000000.0;
        
        if (args->duration > 0 && elapsed >= args->duration) break;
        if (args->duration <= 0 && written >= args->bytes_to_write) break;

        ssize_t res = write(fd, buf, BUFFER_SIZE);
        if (res <= 0) {
            fprintf(stderr, "Thread %d: Write failed: %s\n", args->thread_id, strerror(errno));
            break;
        }
        
        written += res;
        pthread_mutex_lock(args->mutex);
        *(args->total_written) += res;
        pthread_mutex_unlock(args->mutex);

        // If we hit target size but have duration left, loop back to start options:
        // 1. lseek(0) - simplest
        // 2. ftruncate + lseek - cleaner
        if (args->duration > 0 && written >= args->bytes_to_write) {
            lseek(fd, 0, SEEK_SET);
            written = 0; // Reset local written counter to keep looping
        }
    }

    close(fd);
    free(buf);
    return NULL;
}

int main(int argc, char *argv[]) {
    if (argc < 5) {
        printf("Usage: %s <base_path> <size_gb> <duration_sec> <threads>\n", argv[0]);
        return 1;
    }

    const char *base_path = argv[1];
    long long size_gb = atoll(argv[2]);
    int duration = atoi(argv[3]);
    int num_threads = atoi(argv[4]);

    long long bytes_per_thread = (size_gb * 1024 * 1024 * 1024) / num_threads;
    long long total_written = 0;
    pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

    pthread_t threads[num_threads];
    thread_args args[num_threads];

    printf("Partying on your SSD with %d threads...\n", num_threads);

    for (int i = 0; i < num_threads; i++) {
        snprintf(args[i].path, sizeof(args[i].path), "%s/party_%d.tmp", base_path, i);
        args[i].bytes_to_write = bytes_per_thread;
        args[i].duration = duration;
        args[i].total_written = &total_written;
        args[i].mutex = &mutex;
        args[i].thread_id = i;
        if (pthread_create(&threads[i], NULL, write_stress, &args[i]) != 0) {
             fprintf(stderr, "Failed to create thread %d\n", i);
        }
    }

    for (int i = 0; i < num_threads; i++) {
        pthread_join(threads[i], NULL);
    }

    if (total_written == 0) {
        fprintf(stderr, "Error: No data was written!\n");
        return 1;
    }

    printf("PARTY_RESULT:%lld\n", total_written);
    return 0;
}
