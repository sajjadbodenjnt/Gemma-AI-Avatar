# AIBondhu Bangla — শূন্য থেকে প্রশিক্ষণ

এই ফোল্ডারে PyTorch decoder-only Transformer আছে। কোনো pretrained checkpoint, কোম্পানির base model বা LoRA লোড হয় না; weights random initialization থেকে শুরু হয়।

## গুরুত্বপূর্ণ সীমাবদ্ধতা
- এটি training pipeline; এখনো প্রশিক্ষিত বা নির্ভুল মডেল নয়।
- এই repository-তে এখনো বাংলা corpus নেই।
- tokenizer পরীক্ষার জন্য অন্তত 10 MB এবং starter training-এর জন্য অন্তত 2 million token দরকার। বাস্তব মানের বাংলা দক্ষতার জন্য বহু শত মিলিয়ন/বিলিয়ন পরিষ্কার token, বহু GPU এবং দীর্ঘ প্রশিক্ষণ লাগবে।
- সাধারণ GitHub-hosted runner-এ GPU থাকে না। self-hosted Linux x64 GPU runner সংযুক্ত করতে হবে।
- 8 layer, 512 hidden, 8 heads হলো প্রাথমিক গবেষণা-আকার; ChatGPT-স্তরের সক্ষমতার দাবি নয়।

## ডেটা
training_scratch/data/bengali_corpus.txt নামে UTF-8 text দিন। উৎসের লাইসেন্স/অনুমতি নিশ্চিত করুন; ব্যক্তিগত তথ্য, কপিরাইট-অননুমোদিত লেখা, OCR ভুল, spam এবং duplicate বাদ দিন। সংবাদ, বই, কথোপকথন, বিজ্ঞান, গণিত, প্রযুক্তি, আঞ্চলিক ও কথ্য বাংলা ভারসাম্য রাখুন। Train/validation/test split document/source অনুযায়ী করুন।

## চালানো
pip install torch sentencepiece numpy
python training_scratch/train_tokenizer.py
python training_scratch/train.py --steps 100000 --batch 8 --seq 512

ফলাফল output/bangla-from-scratch/final.pt এবং tokenizer। Android/llama.cpp export ও device profiling আলাদা কাজ।

## নির্ভুলতা যাচাই
আলাদা held-out বাংলা benchmark-এ বানান, ব্যাকরণ, সারাংশ, নির্দেশনা, গণিত, যুক্তি ও তথ্যভিত্তিক QA পরীক্ষা এবং মানব মূল্যায়ন আবশ্যক। পরীক্ষা ছাড়া নির্ভুল বলা যাবে না।
