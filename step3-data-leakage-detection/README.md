# Step 3: Data Leakage Detection

**Only tested on Ubuntu 22.04. Please make sure '/home/runner/work/' directory exists and running this tool with root privileges.**

**Make sure that node.js is installed.**

## Build

```shell

./gradlew distZip

```


## Run

```shell

cd build/distributions/

unzip step3-data_leakage_detection.zip

cd step3-data_leakage_detection/bin

./step3-data_leakage_detection {cache_rule_output.txt} {cache_leak_output.txt} {OUT_DIR}

```
