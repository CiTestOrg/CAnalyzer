from .common_utils import *


def match_key(k_path):
    for idx, k in enumerate(reversed(k_path)):
        if k == 'jobs':
            return k_path[len(k_path)-idx]


def recursive_traversal_json_for_use_field(k, yml_json, k_path, use_content_list):
    if isinstance(yml_json, dict):
        for k, v in yml_json.items():
            k_path.append(k)
            if k == "uses":
                use_content = {'job': match_key(k_path), 'uses': v}
                if 'with' in yml_json:
                    use_content['with'] = yml_json['with']
                else:
                    use_content['with'] = {}
                use_content_list.append(use_content)
            if isinstance(v, (dict, list)):
                recursive_traversal_json_for_use_field(k, v, k_path, use_content_list)
            k_path.pop()

    elif isinstance(yml_json, list):
        for item in yml_json:
            recursive_traversal_json_for_use_field(k, item, k_path, use_content_list)
    else:
        pass


def recursive_traversal_json_for_run_field(k, yml_json, k_path, run_content_list):
    if isinstance(yml_json, dict):
        for key, value in yml_json.items():
            k_path.append(key)
            if key == "run":
                run_content = {'run': value, 'job': match_key(k_path)}
                run_content_list.append(run_content)
            if isinstance(value, (dict, list)):
                recursive_traversal_json_for_run_field(key, value, k_path, run_content_list)
            k_path.pop()

    elif isinstance(yml_json, list):
        for item in yml_json:
            recursive_traversal_json_for_run_field(k, item, k_path, run_content_list)
    else:
        pass


def get_binary_data_leak(binary_dict):
    data_leak = []
    with open("binary_leak_map.json", "r") as f:
        binary_leak_file_map = json.load(f)
    intersection_key = set(binary_dict) & set(binary_leak_file_map)
    if len(intersection_key) > 0:
        for key in intersection_key:
            for i in binary_leak_file_map[key]:
                cmd_argv_set = set(i['command'].split(" ")[1:])
                for instr_item in binary_dict[key]:
                    if instr_item['vars'] and cmd_argv_set.issubset(set(instr_item['instructions'])):
                        leak_info = {
                            'filePath': i['leak_files'],
                            'type': "binary",
                            'binary_command': i['command'],
                            'vars': list(set(instr_item['vars'])),
                            'instructions': " ".join(instr_item['instructions'])
                        }
                        data_leak.append(leak_info)
    if "echo" in binary_dict:
        for instr_item in binary_dict["echo"]:
            if instr_item['vars']:
                echo_write_file_list = get_echo_write_files(instr_item['instructions'])
                if echo_write_file_list:
                    leak_info = {
                        "filePath": echo_write_file_list,
                        "type": "echo",
                        "vars": instr_item['vars'],
                        "instruction": " ".join(instr_item['instructions'])
                    }
                    data_leak.append(leak_info)
    return data_leak


def get_plugin_data_leak(info, secrets_dict):
    with open("github_plugin_leak_map.json", "r") as f:
        plugin_leak_file_map = json.load(f)
    use_content_list = []
    recursive_traversal_json_for_use_field("", info['yml_json'], [], use_content_list)
    info_id = info.get('repoName', "") + "@" + info.get('path', "")
    if info_id in secrets_dict:
        info_env_dict = secrets_dict[info_id]
        g_vars = info_env_dict['envSecretsAndVars']
    else:
        info_env_dict = {}
        g_vars = []
    data_leak = []
    for job in use_content_list:
        try:
            job_vars = info_env_dict['jobs'][job['job']]
        except Exception:
            job_vars = []

        current_vars = g_vars + job_vars

        use_match_vars = {}
        if 'uses' in job and 'with' in job:
            if not isinstance(job['uses'], str):
                continue
            plugin_name = job["uses"].rsplit("@", 1)[0]
            use_match_vars[plugin_name] = []
            with_field = job['with']
            if isinstance(with_field, dict):
                for k, v in with_field.items():
                    for var in current_vars:
                        if var in str(v):
                            use_match_vars[plugin_name].append(var)
            elif isinstance(with_field, list):
                for v in with_field:
                    for var in current_vars:
                        if var in str(v):
                            use_match_vars[plugin_name].append(var)
            else:
                for var in current_vars:
                    if var in str(with_field):
                        use_match_vars[plugin_name].append(var)

        plugin_name = job['uses'].rsplit('@', 1)[0]
        with_dict = job['with']
        leak_info = {}
        if plugin_name in plugin_leak_file_map:
            leak_inputs = plugin_leak_file_map[plugin_name]['input_keys']
            leek_files = plugin_leak_file_map[plugin_name]['leak_files']
            intersection_keys = set(leak_inputs) & set(with_dict)
            if len(intersection_keys) > 0:
                leak_info['filePath'] = leek_files
                leak_info['type'] = "plugin"
                leak_info['plugin_name'] = plugin_name
                leak_info['vars'] = use_match_vars.get(plugin_name, [])
                data_leak.append(leak_info)

    return data_leak


def handle_github_actions(info, secrets_dict):
    run_content_list = []
    recursive_traversal_json_for_run_field("", info['yml_json'], [], run_content_list)
    info_id = info.get('repoName', "") + "@" + info.get('path', "")
    if info_id in secrets_dict:
        info_env_dict = secrets_dict[info_id]
        g_vars = info_env_dict['envSecretsAndVars']
    else:
        info_env_dict = {}
        g_vars = []
    binary_dict = {}
    for job in run_content_list:
        job_vars = info_env_dict.get('jobs', {}).get(job['job'], [])
        current_vars = g_vars + job_vars
        bashlex_split_command = list(bashlex_split(job['run']))
        group_split_run_field = split_list(bashlex_split_command)
        for s_list in group_split_run_field:
            hit_vars = get_instr_hit_vars(s_list, current_vars)
            first_command_key = get_first_command_key(s_list)
            if first_command_key == "" and hit_vars:
                first_command_key = "".join(s_list)
            if first_command_key:
                binary_dict.setdefault(first_command_key, []).append({
                    "vars": hit_vars,
                    "instructions": s_list
                })
    binary_data_leak = get_binary_data_leak(binary_dict)
    plugin_data_leak = get_plugin_data_leak(info, secrets_dict)

    all_data_leak = binary_data_leak + plugin_data_leak
    result = {
        "chp": info.get("chp", ""),
        "cp": "GithubActions",
        "repoName": info.get('repoName', ""),
        "path": info.get('path', ""),
        "dataLeak": all_data_leak
    }
    if result['dataLeak']:
        return result
    else:
        return None
