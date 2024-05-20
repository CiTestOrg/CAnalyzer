from .common_utils import *


def recursive_traversal_json(k, yml_json, k_path, run_content_list):
    if isinstance(yml_json, dict):
        for key, value in yml_json.items():
            k_path.append(key)
            if key == "script" or key == "before_script" or key == "after_script":
                if isinstance(value, list):
                    for v in value:
                        if isinstance(v, str):
                            run_content = {'script': v}
                            run_content_list.append(run_content)
                        elif isinstance(v, list):
                            for item in v:
                                if isinstance(item, str):
                                    run_content = {'script': item}
                                    run_content_list.append(run_content)
                                else:
                                    pass
                        else:
                            pass
                elif isinstance(value, str):
                    run_content = {'script': value}
                    run_content_list.append(run_content)

            if isinstance(value, (dict, list)):
                recursive_traversal_json(key, value, k_path, run_content_list)
            k_path.pop()

    elif isinstance(yml_json, list):
        for item in yml_json:
            recursive_traversal_json(k, item, k_path, run_content_list)
    else:
        pass


def handle_gitlab_ci(info, secrets_dict):
    script_content_list = []
    recursive_traversal_json("", info['yml_json'], [], script_content_list)
    info_id = info.get('repoName', "") + "@" + info.get('path', "")
    sensitive_vars = secrets_dict.get(info_id, [])
    binary_dict = convert_to_binary_leak_map(script_content_list, sensitive_vars)
    binary_data_leak = get_binary_data_leak(binary_dict)

    result = {
        "chp": info.get("chp", ""),
        "cp": "GitlabCI",
        "repoName": info.get('repoName', ""),
        "path": info.get('path', ""),
        "dataLeak": binary_data_leak
    }
    if result['dataLeak']:
        return result
    else:
        return None
