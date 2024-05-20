
bitbucket_cache_pre_defined_map = {
    "composer": ["~/.composer/cache"],
    "dotnetcore": ["~/.nuget/packages"],
    "gradle": ["~/.gradle/caches"],
    "ivy2": ["~/.ivy2/cache"],
    "maven": ["~/.m2/repository"],
    "node": ["node_modules"],
    "pip": ["~/.cache/pip"],
    "sbt": ["~/.sbt", "~/.ivy2/cache"]
}


def recursive_traversal_json(k, yml_json, k_path, cache_rule_list):
    if isinstance(yml_json, dict):
        for key, value in yml_json.items():
            k_path.append(key)
            if key == "cache":
                if isinstance(value, list):
                    for item in value:
                        if item in bitbucket_cache_pre_defined_map:
                            cache_rule_list.extend(bitbucket_cache_pre_defined_map[item])
                        else:
                            pass
                            # print(json.dumps(item, indent=4))
                elif isinstance(value, str):
                    if "~" in value:
                        cache_rule_list.append(value)
                    elif value in bitbucket_cache_pre_defined_map:
                        cache_rule_list.extend(bitbucket_cache_pre_defined_map[value])
                    else:
                        pass
                else:
                    pass
            if key == "caches":
                if isinstance(value, list):
                    for item in value:
                        if item in bitbucket_cache_pre_defined_map:
                            cache_rule_list.extend(bitbucket_cache_pre_defined_map[item])
                        else:
                            pass
                elif isinstance(value, dict):
                    for k, v in value.items():
                        if isinstance(v, str):
                            cache_rule_list.append(v)
                else:
                    pass
            if isinstance(value, (dict, list)):
                recursive_traversal_json(key, value, k_path, cache_rule_list)
            k_path.pop()

    elif isinstance(yml_json, list):
        for item in yml_json:
            recursive_traversal_json(k, item, k_path, cache_rule_list)
    else:
        pass



def extract_bitbucket_pipeline(info):
    cache_rule_list = []
    recursive_traversal_json("", info['yml_json'], [], cache_rule_list)
    filtered_cache_rule_list = [x for x in cache_rule_list if not x.startswith("${{") and not x.startswith("{{") and x]
    if filtered_cache_rule_list:
        return {
            "chp": info.get("chp", ""),
            "cp": "BitbucketPipeline",
            "repoName": info.get("repoName", ""),
            "path": info.get("path", ""),
            "cacheRule": list(set(filtered_cache_rule_list))
        }
    else:
        return None
