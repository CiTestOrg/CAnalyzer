import base64
import json
import sys
import traceback
from pathlib import Path

import yaml

from leakage_handler.bitbucket_handler import handle_bitbucket_pipeline
from leakage_handler.circleci_handler import handle_circle_ci
from leakage_handler.github_handler import handle_github_actions
from leakage_handler.gitlab_handler import handle_gitlab_ci
from leakage_handler.travis_handler import handle_travis_ci
from leakage_handler.common_utils import init_secrets_var_dict

from cache_rule_extractor.bitbucket_cache_rule_extractor import extract_bitbucket_pipeline
from cache_rule_extractor.circle_ci_cache_rule_extractor import extract_circle_ci
from cache_rule_extractor.github_cache_rule_extractor import extract_github_actions
from cache_rule_extractor.gitlab_cache_rule_extractor import extract_gitlab_ci
from cache_rule_extractor.travis_cache_rule_extractor import extract_travis_ci



def cache_leakage_analyzer(cp_name, yml_path, secrets_var_path, cache_rule_output_path, cache_leak_output_path):
    if not Path(cache_leak_output_path).parent.exists():
        Path(cache_leak_output_path).parent.mkdir(parents=True, exist_ok=True)
    if not Path(cache_rule_output_path).parent.exists():
        Path(cache_rule_output_path).parent.mkdir(parents=True, exist_ok=True)
    write_cache_leak_output = open(cache_leak_output_path, "w")
    write_cache_rule_output = open(cache_rule_output_path, "w")

    with open(yml_path, "r") as f:
        secrets_dict = init_secrets_var_dict(secrets_var_path, cp_name)
        for line in f:
            info = json.loads(line)
            try:
                yml_json = yaml.safe_load(base64.b64decode(info['content']).decode('utf-8', errors='ignore'))
                if not yml_json:
                    continue
                info['yml_json'] = yml_json
                del info['content']
                if cp_name == "GithubActions":
                    leakage_result = handle_github_actions(info, secrets_dict)
                    cache_rule_result = extract_github_actions(info)
                elif cp_name == "GitlabCI":
                    leakage_result = handle_gitlab_ci(info, secrets_dict)
                    cache_rule_result = extract_gitlab_ci(info)
                elif cp_name == "CircleCI":
                    leakage_result = handle_circle_ci(info, secrets_dict)
                    cache_rule_result = extract_circle_ci(info)
                elif cp_name == "TravisCI":
                    leakage_result = handle_travis_ci(info, secrets_dict)
                    cache_rule_result = extract_travis_ci(info)
                elif cp_name == "BitbucketPipeline":
                    leakage_result = handle_bitbucket_pipeline(info, secrets_dict)
                    cache_rule_result = extract_bitbucket_pipeline(info)
            except yaml.YAMLError:
                # ignore yaml parse error
                pass
            except Exception as e:
                print(e)
            if leakage_result:
                write_cache_leak_output.write(json.dumps(leakage_result) + "\n")
                print(leakage_result)
            if cache_rule_result:
                write_cache_rule_output.write(json.dumps(cache_rule_result) + "\n")
                print(cache_rule_result)
    write_cache_leak_output.close()
    write_cache_rule_output.close()



if __name__ == '__main__':
    args = sys.argv[1:]
    """
    args[0]: cp_name
    args[1]: yml_path
    args[2]: secrets_var_path
    args[3]: cache_rule_output_path
    args[4]: cache_leak_output_path
    """
    cache_leakage_analyzer(args[0], args[1], args[2], args[3], args[4])

    #  below is the test code for function call, you can use the function directly

    # cache_leakage_analyzer("CircleCI",
    #                        "test/input/yml/test_yml_input_for_circleci.txt",
    #                        "test/input/sensitive_secrets/circleci_secrets_vars_result.txt",
    #                        "test/output/cache_rule_output.txt",
    #                        "test/output/cache_leak_output.txt"
    #                        )
    # cache_leakage_analyzer("BitbucketPipeline",
    #                        "test/input/yml/test_yml_input_for_bitbucket_pipelines.txt",
    #                        "test/input/sensitive_secrets/bitbucket_pipelines_secrets_vars_result.txt",
    #                        "test/output/cache_rule_output.txt",
    #                        "test/output/cache_leak_output.txt"
    #                        )
    # cache_leakage_analyzer("GithubActions",
    #                        "test/input/yml/test_yml_input_for_gha.txt",
    #                        "test/input/sensitive_secrets/gha_secrets_vars_result.txt",
    #                        "test/output/cache_rule_output.txt",
    #                        "test/output/cache_leak_output.txt"
    #                        )
    # cache_leakage_analyzer("GitlabCI",
    #                        "test/input/yml/test_yml_input_for_gitlab.txt",
    #                        "test/input/sensitive_secrets/gitlab_secrets_vars_result.txt",
    #                        "test/output/cache_rule_output.txt",
    #                        "test/output/cache_leak_output.txt"
    #                         )
    # cache_leakage_analyzer("TravisCI",
    #                        "test/input/yml/test_yml_input_for_travis.txt",
    #                        "test/input/sensitive_secrets/travis_secrets_vars_result.txt",
    #                        "test/output/cache_rule_output.txt",
    #                        "test/output/cache_leak_output.txt"
    #                        )
