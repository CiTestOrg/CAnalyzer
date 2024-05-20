def recursive_traversal_json(k, yml_json, k_path, cache_rule_list):
    if isinstance(yml_json, dict):
        for k, v in yml_json.items():
            k_path.append(k)
            if k == "uses" and "actions/cache" in v:
                if "with" in yml_json and "path" in yml_json["with"]:
                    if isinstance(yml_json["with"]["path"], list):
                        print(yml_json["with"]["path"])
                        for path in yml_json["with"]["path"]:
                            if isinstance(path, str):
                                if "\n" in path:
                                    split_res = path.split("\n")
                                    cache_rule_list.extend(split_res)
                                else:
                                    cache_rule_list.append(path)
                            else:
                                continue
                    elif isinstance(yml_json["with"]["path"], str):
                        if "\n" in yml_json["with"]["path"]:
                            split_res = yml_json["with"]["path"].split("\n")
                            cache_rule_list.extend(split_res)
                        else:
                            cache_rule_list.append(yml_json["with"]["path"])
                    else:
                        pass
                else:
                    # like acitons/cache-restores may not have path key
                    pass
            else:
                pass
            if isinstance(v, (dict, list)):
                recursive_traversal_json(k, v, k_path, cache_rule_list)
            k_path.pop()

    elif isinstance(yml_json, list):
        for item in yml_json:
            recursive_traversal_json(k, item, k_path, cache_rule_list)
    else:
        pass



def extract_github_actions(info):
    cache_rule_list = []
    recursive_traversal_json("", info["yml_json"], [], cache_rule_list)
    filtered_cache_rule_list = [x for x in cache_rule_list if not x.startswith("${{") and not x.startswith("{{") and x]
    if filtered_cache_rule_list:
        return {
            "chp": info.get("chp", ""),
            "cp": "GithubActions",
            "repoName": info.get("repoName", ""),
            "path": info.get("path", ""),
            "cacheRule": list(set(filtered_cache_rule_list))
        }
    else:
        return None
