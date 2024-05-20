# Step 1: CI Secret Variable Identification

## Build

```shell

./gradlew distZip

```


## Run

```shell

cd build/distributions/

unzip step1-cache_secrets_vars.zip

cd step1-cache_secrets_vars/bin

cp -r {THIS_REPO_ROOT}/raw .  # copy predefined-vars file to the working dir  

./step1-cache_secrets_vars $REPO_DIR $OUT_DIR

```
