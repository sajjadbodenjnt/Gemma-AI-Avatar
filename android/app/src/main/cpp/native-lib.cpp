#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include "llama.cpp/include/llama.h"

static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static std::mutex g_mutex;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aibondhu_mini_MainActivity_nativeLoadModel(JNIEnv *env, jobject, jstring path) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char *p = env->GetStringUTFChars(path, nullptr);
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(p, mp);
    env->ReleaseStringUTFChars(path, p);
    if (!g_model) return JNI_FALSE;
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 2048;
    cp.n_batch = 512;
    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) { llama_model_free(g_model); g_model = nullptr; return JNI_FALSE; }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aibondhu_mini_MainActivity_nativeGenerate(JNIEnv *env, jobject, jstring prompt) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_ctx) return env->NewStringUTF("আগে GGUF মডেল লোড করুন।");
    const char *raw = env->GetStringUTFChars(prompt, nullptr);
    std::string text = "<|im_start|>user\n" + std::string(raw) +
        "<|im_end|>\n<|im_start|>assistant\n";
    env->ReleaseStringUTFChars(prompt, raw);
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens(text.size() + 16);
    int n = llama_tokenize(vocab, text.c_str(), (int)text.size(), tokens.data(),
                           (int)tokens.size(), true, true);
    if (n < 0) return env->NewStringUTF("প্রম্পটটি বেশি বড়।");
    tokens.resize(n);
    llama_memory_clear(llama_get_memory(g_ctx), true);
    llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
    if (llama_decode(g_ctx, batch) != 0) return env->NewStringUTF("মডেল চালাতে সমস্যা হয়েছে।");
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    std::string answer;
    const int max_tokens = 256;
    for (int i = 0; i < max_tokens; ++i) {
        llama_token tok = llama_sampler_sample(sampler, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;
        char piece[256];
        int len = llama_token_to_piece(vocab, tok, piece, sizeof(piece), 0, true);
        if (len > 0) answer.append(piece, len);
        llama_batch next = llama_batch_get_one(&tok, 1);
        if (llama_decode(g_ctx, next) != 0) break;
    }
    llama_sampler_free(sampler);
    return env->NewStringUTF(answer.c_str());
}
