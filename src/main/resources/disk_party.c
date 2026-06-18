#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <pthread.h>
#include <string.h>
#include <time.h>
#include <sys/time.h>
#include <errno.h>

#define BUFFER_SIZE (2 * 1024 * 1024) // 2MB blocks
#define ALIGNMENT 4096
#define MEM_STRESS_SIZE (64 * 1024 * 1024) // 64MB of memory "churn" per thread

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
    // O_DSYNC is often faster than O_SYNC but still bypasses write cache
    int flags = O_WRONLY | O_CREAT | O_TRUNC;
    
#ifdef O_DSYNC
    flags |= O_DSYNC;
#else
    flags |= O_SYNC;
#endif

#ifdef O_DIRECT
    flags |= O_DIRECT;
#endif

    int fd = open(args->path, flags, 0644);
    if (fd < 0) {
        // Fallback without O_DIRECT if it fails (e.g. filesystem doesn't support it)
        flags &= ~O_DIRECT;
        fd = open(args->path, flags, 0644);
        if (fd < 0) {
            fprintf(stderr, "Thread %d: Failed to open file '%s': %s\n", args->thread_id, args->path, strerror(errno));
            return NULL;
        }
    }

#ifdef F_NOCACHE
    // Darwin specific: bypass HFS+/APFS cache
    if (fcntl(fd, F_NOCACHE, 1) == -1) {
        // Not fatal, just less violent
    }
#endif

    // Allocate aligned buffer for O_DIRECT
    char *buf;
    if (posix_memalign((void **)&buf, ALIGNMENT, BUFFER_SIZE) != 0) {
        fprintf(stderr, "Thread %d: Failed to allocate aligned memory\n", args->thread_id);
        close(fd);
        return NULL;
    }
    
    // Allocate memory stress buffer (outside JVM heap)
    char *mem_churn = malloc(MEM_STRESS_SIZE);
    if (mem_churn) memset(mem_churn, 0, MEM_STRESS_SIZE);

    struct timeval start, now;
    gettimeofday(&start, NULL);
    long long written = 0;
    unsigned int seed = (unsigned int)time(NULL) + args->thread_id;

    while (1) {
        gettimeofday(&now, NULL);
        double elapsed = (now.tv_sec - start.tv_sec) + (now.tv_usec - start.tv_usec) / 1000000.0;
        
        if (args->duration > 0 && elapsed >= args->duration) break;
        if (args->duration <= 0 && written >= args->bytes_to_write) break;

        // 1. Memory Violence: Churn memory to saturate bus and cache
        if (mem_churn) {
            for (int j = 0; j < 4; j++) {
                int offset = rand_r(&seed) % (MEM_STRESS_SIZE - 1024);
                memset(mem_churn + offset, (char)rand_r(&seed), 1024);
            }
        }

        // 2. Disk Violence: Write and force sync
        ssize_t res = write(fd, buf, BUFFER_SIZE);
        if (res <= 0) {
            fprintf(stderr, "Thread %d: Write failed: %s\n", args->thread_id, strerror(errno));
            break;
        }
        
        // Advise kernel to drop cache for this range
#ifdef POSIX_FADV_DONTNEED
        posix_fadvise(fd, written, res, POSIX_FADV_DONTNEED);
#endif

        written += res;
        pthread_mutex_lock(args->mutex);
        *(args->total_written) += res;
        pthread_mutex_unlock(args->mutex);

        if (args->duration > 0 && written >= args->bytes_to_write) {
            lseek(fd, 0, SEEK_SET);
            written = 0; 
        }
    }

    close(fd);
    free(buf);
    if (mem_churn) free(mem_churn);
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

    printf("Initiating violent hardware abuse with %d threads...\n", num_threads);

    struct timeval start_total, end_total;
    gettimeofday(&start_total, NULL);

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

    gettimeofday(&end_total, NULL);
    double total_elapsed = (end_total.tv_sec - start_total.tv_sec) + (end_total.tv_usec - start_total.tv_usec) / 1000000.0;

    if (total_written == 0) {
        fprintf(stderr, "Error: No data was written!\n");
        return 1;
    }

    printf("PARTY_RESULT:%lld:%.2f\n", total_written, total_elapsed);
    return 0;
}
