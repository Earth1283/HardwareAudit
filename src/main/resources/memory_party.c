#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <unistd.h>
#include <time.h>

typedef struct {
    int duration;
    size_t block_size;
    int thread_id;
    long long *total_churned;
    pthread_mutex_t *mutex;
} mem_args;

void *mem_violence(void *arg) {
    mem_args *args = (mem_args *)arg;
    char *buffer = malloc(args->block_size);
    if (!buffer) return NULL;

    time_t end = time(NULL) + args->duration;
    unsigned int seed = (unsigned int)time(NULL) + args->thread_id;
    long long local_churn = 0;

    while (time(NULL) < end) {
        for (int i = 0; i < 1000; i++) {
            size_t offset = rand_r(&seed) % (args->block_size - 64);
            memset(buffer + offset, (char)rand_r(&seed), 64);
            local_churn += 64;
        }

        if (local_churn > 1024 * 1024) {
            pthread_mutex_lock(args->mutex);
            *(args->total_churned) += local_churn;
            pthread_mutex_unlock(args->mutex);
            local_churn = 0;
        }
    }

    pthread_mutex_lock(args->mutex);
    *(args->total_churned) += local_churn;
    pthread_mutex_unlock(args->mutex);

    free(buffer);
    return NULL;
}

long long run_memory_test(int duration, int threads, long long total_mem_to_use) {
    pthread_t *thread_ids = malloc(sizeof(pthread_t) * threads);
    mem_args *args = malloc(sizeof(mem_args) * threads);
    long long total_churned = 0;
    pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

    size_t per_thread = (size_t)(total_mem_to_use / threads);

    for (int i = 0; i < threads; i++) {
        args[i].duration = duration;
        args[i].block_size = per_thread;
        args[i].thread_id = i;
        args[i].total_churned = &total_churned;
        args[i].mutex = &mutex;
        pthread_create(&thread_ids[i], NULL, mem_violence, &args[i]);
    }

    for (int i = 0; i < threads; i++) {
        pthread_join(thread_ids[i], NULL);
    }

    free(thread_ids);
    free(args);

    return total_churned;
}
