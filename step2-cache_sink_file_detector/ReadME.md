# Cache Leakage Analyzer

## Environment
- python >= 3.10.x
- bashlex==0.18
- pyyaml==6.0.1

## Usage
1. Install the required packages
    ```shell
    pip install -r requirements.txt
    ```

2. Run the main.py script with the following 5 arguments:
    - CI Platform name
    - Path to the input file containing the CI YAML content (from step 1)
    - Path to the input file containing the sensitive secrets (from step 1)
    - Path to the output file for cache rules
    - Path to the output file for cache leakage

    ```shell
    python3 main.py \
            CircleCI \
            test/input/yml/test_yml_input_for_circleci.txt \
            test/input/sensitive_secrets/circleci_secrets_vars_result.txt \
            test/output/cache_rule_output.txt \
            test/output/cache_leak_output.txt
    ```