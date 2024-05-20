def recursive_traversal_json(k, yml_json, k_path, cache_rule_list):
    if isinstance(yml_json, dict):
        for key, value in yml_json.items():
            k_path.append(key)
            if key == "save_cache":
                if value and 'paths' in value:
                    if isinstance(value['paths'], str):
                        cache_rule_list.append(value['paths'])
                    elif isinstance(value['paths'], list):
                        for rule in value['paths']:
                            if isinstance(rule, str):
                                cache_rule_list.append(rule)
                            else:
                                pass  # nothing here
                    else:
                        pass  # nothing here
                else:
                    # has embedded cache_save key, not use recursive handle
                    pass

            if isinstance(value, (dict, list)):
                recursive_traversal_json(key, value, k_path, cache_rule_list)
            k_path.pop()

    elif isinstance(yml_json, list):
        for item in yml_json:
            recursive_traversal_json(k, item, k_path, cache_rule_list)
    else:
        pass


def extract_circle_ci(info):
    cache_rule_list = []
    recursive_traversal_json("", info['yml_json'], [], cache_rule_list)
    filtered_cache_rule_list = [x for x in cache_rule_list if not x.startswith("${{") and not x.startswith("{{") and x]
    if filtered_cache_rule_list:
        return {
            "chp": info.get("chp", ""),
            "cp": "CircleCI",
            "repoName": info.get("repoName", ""),
            "path": info.get("path", ""),
            "cacheRule": list(set(filtered_cache_rule_list))
        }
    else:
        return None
