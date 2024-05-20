from .common_utils import *


def recursive_traversal_json(k, yml_json, k_path, run_content_list):
    # traverse the json file to get the run field
    if isinstance(yml_json, dict):
        for k, v in yml_json.items():
            k_path.append(k)
            if k == "run":
                if isinstance(v, str):
                    run_content = {'script': v}
                    run_content_list.append(run_content)
                elif isinstance(v, list):
                    pass
                elif isinstance(v, dict):
                    if 'command' in v and isinstance(v['command'], str):
                        run_content = {'script': v['command']}
                        run_content_list.append(run_content)
                    else:
                        pass
            if isinstance(v, (dict, list)):
                recursive_traversal_json(k, v, k_path, run_content_list)
            k_path.pop()
    elif isinstance(yml_json, list):
        for item in yml_json:
            recursive_traversal_json(k, item, k_path, run_content_list)
    else:
        pass


def get_orbs_data_leak(yml_json):
    with open("circle_ci_orbs_leak_map.json", "r") as f:
        orb_write_file_map = json.load(f)
    orbs_list = []
    if 'orbs' in yml_json and yml_json['orbs']:
        for k, v in yml_json['orbs'].items():
            if isinstance(v, str):
                orbs_list.append(v.rsplit("@", 1)[0])
            else:
                pass
    data_leak = []
    for item in orbs_list:
        if item in orb_write_file_map:
            orb_leak_files = orb_write_file_map[item]['file_list']
            leak_info = {
                'filePath': orb_leak_files,
                'type': "plugin",
                "plugin_name": item,
            }
            data_leak.append(leak_info)
    return data_leak


def handle_circle_ci(info, secrets_dict):
    script_content_list = []
    recursive_traversal_json("", info['yml_json'], [], script_content_list)
    info_id = info.get('repoName', "") + "@" + info.get('path', "")
    sensitive_vars = secrets_dict.get(info_id, [])
    binary_dict = convert_to_binary_leak_map(script_content_list, sensitive_vars)
    binary_data_leak = get_binary_data_leak(binary_dict)
    orbs_data_leak = get_orbs_data_leak(info['yml_json'])

    all_data_leak = orbs_data_leak + binary_data_leak

    result = {
        "chp": info.get("chp", ""),
        "cp": "CircleCI",
        "repoName": info.get('repoName', ""),
        "path": info.get('path', ""),
        "dataLeak": all_data_leak
    }
    if result['dataLeak']:
        return result
    else:
        return None
