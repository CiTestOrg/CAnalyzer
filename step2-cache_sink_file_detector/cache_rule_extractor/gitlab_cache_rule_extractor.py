def recursive_traversal_json(k, yml_json, k_path, cache_rule_list):
    if isinstance(yml_json, dict):
        for key, value in yml_json.items():
            k_path.append(key)
            if key == "cache":
                if isinstance(value, dict):
                    if "paths" in value:
                        if isinstance(value['paths'], list):
                            for v in value['paths']:
                                cache_rule_list.append(v)
                        else:
                            pass  # nothing here
                elif isinstance(value, list):
                    for item in value:
                        if isinstance(item, dict):
                            if "paths" in item:
                                if isinstance(item['paths'], list):
                                    for v in item['paths']:
                                        cache_rule_list.append(v)
                                else:
                                    pass  # nothing here
                        elif isinstance(item, str):
                            cache_rule_list.append(item)
                        else:
                            pass
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


def extract_gitlab_ci(info):
    cache_rule_list = []
    recursive_traversal_json("", info['yml_json'], [], cache_rule_list)

    filtered_cache_rule_list = [x for x in cache_rule_list if not x.startswith("${{") and not x.startswith("{{") and x]
    if filtered_cache_rule_list:
        return {
            "chp": info.get("chp", ""),
            "cp": "GitlabCI",
            "repoName": info.get("repoName", ""),
            "path": info.get("path", ""),
            "cacheRule": list(set(filtered_cache_rule_list))
        }
    else:
        return None

