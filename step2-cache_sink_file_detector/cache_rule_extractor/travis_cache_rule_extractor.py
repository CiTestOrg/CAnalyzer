def recursive_traversal_json(k, yml_json, k_path, cache_rule_list):
    if isinstance(yml_json, dict):
        for key, value in yml_json.items():
            k_path.append(key)
            if key == "cache":
                if isinstance(value, str):
                    if value in travis_cache_shortcut_map:
                        cache_rule_list.extend(travis_cache_shortcut_map[value])
                    else:
                        pass
                elif isinstance(value, dict):
                    if "directories" in value and value["directories"] is not None:
                        if isinstance(value["directories"], str):
                            cache_rule_list.append(value["directories"])
                        elif isinstance(value["directories"], list):
                            for item in value["directories"]:
                                if isinstance(item, str):
                                    cache_rule_list.append(item)
                                else:
                                    pass
                    elif set(value) & set(travis_cache_shortcut_map):
                        for short_cut in set(value.keys()) & set(travis_cache_shortcut_map):
                            if value[short_cut] is True:
                                cache_rule_list.extend(travis_cache_shortcut_map[short_cut])
                            else:
                                pass
                    else:
                        pass
                elif isinstance(value, list):
                    for item in value:
                        if isinstance(item, str):
                            if item in travis_cache_shortcut_map:
                                cache_rule_list.extend(travis_cache_shortcut_map[item])
                            elif item == "apt":
                                pass
                            else:
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


travis_cache_shortcut_map = {
    "bundler": [
        "vendor/bundle"
    ],
    "pip": [
        "$HOME/.cache/pip"
    ],
    "packages": [
        "$HOME/R/Library"
    ],
    "cocoapods": [
        "$HOME/.npm"
    ],
    "yarn": [
        "$HOME/.cache/yarn."
    ],
    "ccache": [
        "$HOME/.ccache"
    ],
    "cargo": [
        "$HOME/.cargo",
        "$TRAVIS_BUILD_DIR/target"
    ],
    "npm": [
        "$HOME/.npm"
    ],
}


def extract_travis_ci(info):
    cache_rule_list = []
    recursive_traversal_json("", info['yml_json'], [], cache_rule_list)
    filtered_cache_rule_list = [x for x in cache_rule_list if not x.startswith("${{") and not x.startswith("{{") and x]
    if filtered_cache_rule_list:
        return {
            "chp": info.get("chp", ""),
            "cp": "TravisCI",
            "repoName": info.get("repoName", ""),
            "path": info.get("path", ""),
            "cacheRule": list(set(filtered_cache_rule_list))
        }
    else:
        return None
