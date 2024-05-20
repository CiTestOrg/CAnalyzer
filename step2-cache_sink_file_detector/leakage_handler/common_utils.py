import json
import bashlex


def bashlex_split(run_field):
    bashlex_split_command = []
    try:
        bashlex_split_command = list(bashlex.split(run_field))
    except Exception:
        # ignore invalid command
        pass
    return bashlex_split_command


def read_secret_file_as_dict(file_path):
    # Except for gitHub, other CI do not need to distinguish the secret of job scope, only a global secret_list
    res = {}
    with open(file_path, "r") as f:
        lines = f.readlines()
        for line in lines:
            info = json.loads(line)
            info_id = info.get('repoName', "") + "@" + info.get('path', "")
            res.setdefault(info_id, [])
            if info['envSecretsAndVars']:
                res[info_id].extend(info['envSecretsAndVars'])
                res[info_id] = list(set(res[info_id]))
    return res


def read_secret_file_as_dict_for_github(file_path):
    # For gitHub, the secret of each job is different, so we need to distinguish the secret of each job
    secret_dict = {}
    with open(file_path, "r") as f:
        lines = f.readlines()
        for line in lines:
            info = json.loads(line)
            info_id = info['repoName'] + "@" + info['path']
            secret_dict.setdefault(info_id, {})
            secret_dict[info_id]['envSecretsAndVars'] = info['envSecretsAndVars']
            secret_dict[info_id]['jobs'] = {}
            for job in info['jobs']:
                secret_dict[info_id]['jobs'][job['job']] = job['secrets']
    return secret_dict


def split_list(input_list):
    new_list = []
    sub_list = []
    for item in input_list:
        # If a special character is encountered, the current sub-list is added to the new list,
        # and a new empty sub-list is created
        if item in ['\n', '&&', '||', ';', '|', '&']:
            if sub_list:
                new_list.append(sub_list)
                sub_list = []
        # If the current element is not a special character, add it to the current sub-list
        else:
            sub_list.append(item)
    # Add the last sub-list to the new list
    if sub_list:
        new_list.append(sub_list)
    return new_list



def get_instr_hit_vars(s_list, current_vars):
    bash_used_vars = []
    s_append = bash_used_vars.append
    for s in s_list:
        # traverse each fragment of an instruction to find the interested command part
        for var in current_vars:
            if var in s:
                s_append(var)
    return bash_used_vars


def get_first_command_key(s_list):
    first_command_key = ""
    # command that will execute shell script or a file
    exec_shell_commands = ['bash', 'sh', 'zsh', 'ksh', 'csh', 'fish', 'python', 'python3', 'perl', 'ruby', 'php', 'tcl',
                           'node', 'lua', 'go']
    # some logical condition keywords in shell
    unix_logical_words = ['if', 'fi', 'then', 'else', 'exit', 'true', 'do', 'done', 'for', '(', ')', '{', '[', 'not',
                          'EOF']
    for s in s_list:
        # s is run field be split into multiple instructions, the first keyword of each instruction.
        if s in unix_logical_words:
            break
        elif s == "sudo":
            continue
        elif s in exec_shell_commands:
            continue
        elif s.startswith("-"):
            continue
        else:
            first_command_key = s  # find the first key word, then break
            break

    return first_command_key


def init_secrets_var_dict(secrets_var_path, cp_name):
    if cp_name == "GithubActions":
        secrets_dict = read_secret_file_as_dict_for_github(secrets_var_path)
    else:
        secrets_dict = read_secret_file_as_dict(secrets_var_path)
    # print(f"Successfully load {len(secrets_dict)} secrets vars")
    return secrets_dict


def get_index(lst=None, item=''):
    return [index for (index, value) in enumerate(lst) if value == item]


def get_echo_write_files(instr):
    file_list = []
    try:
        index1 = get_index(instr, ">")
        index2 = get_index(instr, ">>")
        if len(index1):
            file_list = [instr[idx + 1] for idx in index1]
        elif len(index2):
            file_list = [instr[idx + 1] for idx in index2]
        else:
            file_list = []
        file_list = [x for x in file_list if x != "/dev/null"]
    except Exception:
        pass
    return file_list


def get_binary_data_leak(binary_dict):
    # from extracted command to find the leak file
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


def convert_to_binary_leak_map(script_content_list, sensitive_vars):
    # from script content list to extract the command and sensitive vars
    binary_dict = {}
    for script_content in script_content_list:
        command = script_content.get('script', '')
        bashlex_split_command = bashlex_split(command)
        group_split_run_field = split_list(bashlex_split_command)
        for s_list in group_split_run_field:
            hit_vars = get_instr_hit_vars(s_list, sensitive_vars)
            first_command_key = get_first_command_key(s_list)
            if first_command_key == "" and hit_vars:
                first_command_key = "".join(s_list)
            if first_command_key:
                binary_dict.setdefault(first_command_key, []).append({
                    "vars": hit_vars,
                    "instructions": s_list
                })
    return binary_dict

