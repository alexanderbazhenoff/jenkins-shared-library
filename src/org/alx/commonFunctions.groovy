package org.alx


/**
 * Jenkins shared library. Written by Aleksandr Bazhenov, 2021-2023.
 * Common functions to use in jenkins pipelines.
 *
 * This Source Code Form is subject to the terms of the Apache License v2.0.
 * If a copy of this source file was not distributed with this file, You can obtain one at:
 * https://github.com/alexanderbazhenoff/jenkins-shared-library/blob/main/LICENSE
 */


import groovy.text.StreamingTemplateEngine
import org.codehaus.groovy.runtime.StackTraceUtils

import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient

import org.apache.commons.lang.RandomStringUtils

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

import org.apache.commons.io.FilenameUtils

import hudson.model.Result


/**
 * Global variables for library file CommonFunctions.groovy
 */
class OrgAlxGlobals {
    /**
     * Provide Git credentials ID for git authorisation.
     */
    public static String GitCredentialsID = ''

    /**
     * Provide default verbose level for send message to mattermost function.
     */
    public static Integer MattermostMessageDefaultVerboseLevel = 1

    /**
     * Provide default length of mattermost message.
     */
    public static Integer MattermostMessageDefaultLength = 4000

    /**
     * Provide default time-out for wait ssh host up or down (in minutes).
     */
    public static Integer WaitSshHostUpDownTimeout = 1

    /**
     * Provide default path of home folder for jenkins user.
     */
    public static String JenkinsUserDefaultHomeFolder = '/var/lib/jenkins'

    /**
     * Provide default ansible installation predefined in jenkins Global Configuration Tool.
     */
    public static String AnsibleInstallationName = 'home_local_bin_ansible'
}


/**
 * Add current step (or phase) state to state map and (optional) save steps states with URLs to log file.
 *
 * @param states - map with pipeline steps (or phases) names and states.
 * @param name - name of the current step (or phase).
 * @param state - current state: false|true.
 * @param jobUrl - url of the last job run.
 * @param logName - path and name of the logfile to save (or leave them blank to skip saving).
 * @param printErrorMessage - true to print error message on status error, otherwise print only debug messages.
 * @return - map with pipeline steps (or phases) names and states including current step (or phase) state. The
 *           structure of this map should be: key is the name with spaces cut, value should be a map of:
 *           [name: name, state: state, url: url].
 */
Map addPipelineStepsAndUrls(Map states, String name, Boolean state, String jobUrl, String logName = '',
                            Boolean printErrorMessage = true) {
    Integer eventNumber = !state && printErrorMessage ? 3 : 0
    if (!jobUrl?.trim()) jobUrl = ''
    states[name.replaceAll(' ', '')] = [name: name, state: state, url: jobUrl]
    if (logName?.trim()) {
        String statesTextTable = ''
        states.each { key, value ->
            if (value instanceof Map) {
                statesTextTable += String.format('%s %s %s\n, ', value.name.padLeft(16), value.state.toString().
                        replace('false', '[FAILED] ').replace('true', '[SUCCESS]'), value.url.padLeft(2))
            }
        }
        writeFile file: logName, text: statesTextTable
        archiveArtifacts allowEmptyArchive: true, artifacts: logName
    }
    outMsg(eventNumber, String.format('%s: %s, URL: %s', name, state.toString().replace('false', 'FAILED')
            .replace('true', 'SUCCESS'), jobUrl))
    return states
}

/**
 * Make jenkins job/pipeline parameters human readable.
 *
 * @param params - list of job/pipeline params.
 * @return - string of human readable params list.
 */
static String readableJobParams(ArrayList params) {
    return params.toString().replaceAll('\\[', '[\n\t').replaceAll(']', '\n]')
            .replaceAll('\\), ', '),\n\t')
}

/**
 * Convert map to jenkins job params.
 *
 * @param config - config input in the next keys format:
 *
 *                 name: config name (or just visible name);
 *                 enabled: true|false (just the way to enable/disable without parameters removal);
 *                 jobname: jenkins job/pipeline name to execute (will be skipped if wasn't set on checkName=false,
 *                 or will return an empty arrayList on checkName=true);
 *
 *                 Other key-value parameters in this map will be converted to pipeline parameters, where:
 *
 *                 key - pipeline/job parameter name to pass downstream pipeline/job;
 *                 value - pipeline/job parameter value: Boolean will be boolean, List will pass as comma separated
 *                 string, other types will be converted to string.
 *
 * @param checkName - check value of 'name' key exists in map, otherwise ignore.
 * @return - jenkins job params arrayList.
 */
ArrayList mapToJenkinsJobParams(Map config, Boolean checkName = true) {
    String configName
    ArrayList jobParams = []
    try {
        if (config.get('name')) {
            configName = config.name
        } else {
            if (checkName) {
                outMsg(3, '\'name\' param not found, please set visible name.')
                return jobParams
            } else {
                configName = '<undefined_config_name>'
            }
        }
        if (config.get('enabled') && config.get('jobname')) {
            if (config.enabled && config.jobname?.trim()) {
                outMsg(0, String.format('Processing current %s config: \n%s', configName,
                        (new JsonBuilder(config).toPrettyString())))
                jobParams = mapConfigToJenkinsJobParam(config.findAll {
                    !it.key.startsWith('msg') && it.key != 'name' && it.key != 'enabled' && it.key != 'jobname'
                })
                outMsg(0, String.format('Config %s includes the next pipeline params: \n%s', configName,
                        readableJobParams(jobParams)))
            } else if (config.enabled && !config.jobname?.trim()) {
                outMsg(2, String.format('%s %s %s', 'Unable to perform jenkins job parameters conversion for',
                        configName, 'jobname in this config wasn\'t set. Skipping job run for this config.'))
            } else if (!config.enabled) {
                outMsg(1, String.format('%s config disabled, skipping.', configName))
            }
        } else {
            outMsg(3, String.format('%s %s %s', "Unable to find 'enabled' and/or 'jobname' param(s) in",
                    configName, 'config. Please check and try again. Config was skipped.'))
        }
    } catch (Exception err) {
        outMsg(3, String.format('Converting yaml config to jenkins job params error: %s', readableError(err)))
    }
    return jobParams
}

/**
 * More readable exceptions with line numbers.
 *
 * @param error - Exception error.
 */
static String readableError(Throwable error) {
    return String.format('Line %s: %s', error.stackTrace.head().lineNumber, StackTraceUtils.sanitize(error))
}

/**
 * Parse map item and return them as arraylist for jenkins pipeline job param.
 *
 * @param key - item key name.
 * @param value - item key value.
 * @param type - (optional) item parameter type: string, choice, boolean, text, password or undefined to autodetect.
 * @param upperCaseKeyName - (optional) true when convert parameters to uppercase is required.
 * @param params - (optional) other parameters to add to them.
 * @return - array list for jenkins pipeline job parameters.
 */
ArrayList itemKeyToJobParam(String key, def value, String type = '', Boolean upperCaseKeyName = true,
                            ArrayList params = []) {
    String keyName = upperCaseKeyName ? key.toUpperCase() : key
    params += value instanceof Boolean || type == 'boolean' ? [booleanParam(name: keyName, value: value)] : []
    params += value instanceof ArrayList ? string(name: keyName, value: value.toString().replaceAll(',', '')) : []
    params += value instanceof String && (type == 'string' || !type?.trim()) ?
            [string(name: keyName, value: value)] : []
    params += value instanceof String && type == 'text' ? [text(name: keyName, value: value)] : []
    params += value instanceof String && type == 'password' ? [password(name: keyName, value: value)] : []
    params += value instanceof Integer || value instanceof Float || value instanceof BigInteger ?
            [string(name: keyName, value: value.toString())] : []
    return params
}

/**
 * Convert map of jenkins pipeline params to arrayList which path is required to run a new build of jenkins job.
 *
 * @param mapConfig - Map with the whole pipeline params.
 * @return - array list for jenkins pipeline running, e.g: build job: 'job_name', parameters: these_params.
 */
ArrayList mapConfigToJenkinsJobParam(Map mapConfig) {
    ArrayList jobParams = []
    mapConfig.each {
        String paramPrefix = ''
        if (it.value instanceof Map) {
            paramPrefix = it.key.toString()
            it.value.each { k, v -> jobParams += itemKeyToJobParam(String.format('%s_%s', paramPrefix, k), v) }
        } else {
            jobParams += itemKeyToJobParam(it.key.toString(), it.value)
        }
    }
    return jobParams
}

/**
 * Print colored event type, jenkins job name and message to job console.
 *
 * @param eventNumber - event type: debug, info, warning or error. Debug event output available when DEBUG_MODE pipeline
 *                      parameter is true.
 * @param envVariables - environment variables (env which is class org.jenkinsci.plugins.workflow.cps.EnvActionImpl).
 * @param text - text to output.
 */
def outMsg(Integer eventNumber, String text, Object envVariables = env) {
    if (eventNumber.toInteger() != 0 || envVariables.getEnvironment().get('DEBUG_MODE')?.toBoolean()) {
        wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']) {
            ArrayList eventTypes = [
                    '\033[0;34mDEBUG\033[0m',
                    '\033[0;32mINFO\033[0m',
                    '\033[0;33mWARNING\033[0m',
                    '\033[0;31mERROR\033[0m']
            println String.format('%s | %s | %s', envVariables.JOB_NAME, eventTypes[eventNumber], text)
        }
    }
}

/**
 * Post HTTP request.
 *
 * @param httpUrl - http(s) URL.
 * @param data - data to send.
 * @param headerType - header encoding type (e.g: 'application/x-www-form-urlencoded').
 * @param contentType - content encoding (e.g: 'application/json', 'text/xml').
 * @return - status map:
 *           status.request_line - POST request line,
 *           status.response_status_line - response status line (e.g: 'HTTP/1.1 200 OK'),
 *           status.content_length - byte length of the HTTP body (read:
 *                          https://stackoverflow.com/questions/2773396/whats-the-content-length-field-in-http-header),
 *           status.response_is_chunked - response is chunked (read:
 *                                        https://ru.wikipedia.org/wiki/Chunked_transfer_encoding),
 *           status.response_content_encoding - encoding of response (none if not encoded),
 *           status.response_content - content of http response (e.g: 'ok').
 */
static httpsPost(String httpUrl, String data, String headerType, String contentType) {
    HttpClient httpClient = new DefaultHttpClient()
    HttpResponse response
    Map status = [:]
    try {
        HttpPost httpPost = new HttpPost(httpUrl)
        httpPost.setHeader("Content-Type", headerType)

        HttpEntity reqEntity = new StringEntity(data, "UTF-8")
        reqEntity.setContentType(contentType)
        reqEntity.setChunked(true)

        httpPost.setEntity(reqEntity)
        status.request_line = httpPost.getRequestLine()

        response = httpClient.execute(httpPost)
        HttpEntity resEntity = response.getEntity()

        status.response_status_line = response.getStatusLine()
        if (resEntity != null) {
            status.content_length = resEntity.getContentLength()
            status.response_is_chunked = resEntity.isChunked()
            status.response_content_encoding = resEntity.contentEncoding
            status.response_content = resEntity.content.text
        }
    } finally {
        httpClient.getConnectionManager().shutdown()
    }
    return status
}

/**
 * Send single message to mattermost.
 *
 * @param url - url including token (e.g: https://mattermost.com/hooks/<token>).
 * @param text - text.
 * @param verboseLevel - level of verbosity, 2 - debug, 1 - normal, 0 - disable.
 * @return - true when http OK 200.
 */
Boolean sendMattermostChannelSingleMessage(String url, String text,
                                           Integer verboseLevel = OrgAlxGlobals.MattermostMessageDefaultVerboseLevel) {
    Map mattermostResponse = httpsPost(url, String.format('''payload={"text": "%s"}''', text),
            'application/x-www-form-urlencoded', 'application/JSON;charset=UTF-8')
    Map mattermostResponseData = mattermostResponse.findAll { it.key != 'request_line' }
    if (verboseLevel > 0) println mattermostResponseData
    if (mattermostResponseData) {
        if (verboseLevel == 2) println String.format('Sending mattermost: %s', readableMap(mattermostResponseData))
    } else {
        println 'Sending mattermost: Error, no http response.'
    }
    if (mattermostResponse.get('response_status_line')) {
        if (verboseLevel >= 1) println String.format('Sending mattermost: %s', mattermostResponse.response_status_line)
        return (mattermostResponse['response_status_line'].toString().contains('200 OK'))
    } else {
        println String.format('Sending mattermost: %s', (mattermostResponse.get('response_content') ?
                mattermostResponse.response_content : '<null or empty>'))
        return false
    }
}

/**
 * Send message to mattermost with split by 4000 symbols.
 *
 * @param url - url including token (e.g: https://mattermost.com/hooks/<token>).
 * @param text - text.
 * @param verboseMsg - level of verbosity, 2 - debug, 1 - normal, 0 - disable.
 * @param messageLength - length of sing mattermost message possible to send.
 * @return - true when http OK 200.
 */
Boolean sendMattermostChannel(String url, String text, Integer verboseMsg,
                              Integer messageLength = OrgAlxGlobals.MattermostMessageDefaultLength) {
    Boolean overallSendMessageState = true
    if (text.length() >= messageLength) {
        ArrayList splitMessages = []
        List splitByEnterMessage = text.tokenize('\n').toList()
        Integer messageIndex = 0
        splitMessages[messageIndex] = ''
        Integer codeBlockMarkersCounter = 0
        for (Integer i = 0; i < splitByEnterMessage.size(); i++) {
            if (splitByEnterMessage[i].find('```'))
                codeBlockMarkersCounter++
            String tempMessageString = String.format('%s\n%s', splitMessages[messageIndex], splitByEnterMessage[i])
            if (tempMessageString.size() <= messageLength - 4) {
                splitMessages[messageIndex] = tempMessageString
            } else {
                if (codeBlockMarkersCounter % 2 == 0) {
                    messageIndex++
                    splitMessages[messageIndex] = ''
                } else {
                    splitMessages[messageIndex] += '\n```'
                    messageIndex++
                    splitMessages[messageIndex] = '```'
                }
            }
        }
        splitMessages.each {
            if (!sendMattermostChannelSingleMessage(url, it.toString(), verboseMsg))
                overallSendMessageState = false
        }
        return overallSendMessageState
    } else {
        return sendMattermostChannelSingleMessage(url, text, verboseMsg)
    }
}

/**
 * Password generator.
 *
 * @param passwordLength - number of symbols to be generated.
 * @return - password.
 */
static String passwordGenerator(Integer passwordLength) {
    String charset = (('A'..'Z') + ('a'..'z') + ('0'..'9') + ('+-*#$@!=%') - ('0O1Il')).join('')
    return (new RandomStringUtils().random(passwordLength, charset.toCharArray()))
}

/**
 * More readable Map output.
 *
 * @param content - map content.
 * @return - human-readable string of map.
 */
static String readableMap(Map content) {
    return (new JsonBuilder(content).toPrettyString())
}

/**
 * Transform json String input into a serializable HashMap.
 *
 * @param txt - json text input.
 * @return - serializable HashMap.
 */
@NonCPS
static Map parseJson(String txt) {
    Map map = [:]
    new JsonSlurper().parseText(txt).each { prop ->
        map[prop.key] = prop.value
    }
    return map
}

/**
 * Transliterate string from cyrillic to latin.
 *
 * @param text - text to transliterate.
 * @return - transliterated text.
 */
String transliterateString(String text) {
    return (sh(returnStdout: true, script: String.format('%s %s', 'python3 -c "import sys; from transliterate import',
            '''translit; print(translit(sys.stdin.read(), 'ru', reversed=True), end='')" <<< "%s"''', text)).trim())
}

/**
 * Transform yaml ArrayList input into a LazyMap
 * (if you parse yaml list like it's in ansible and get from readYaml an array list you'll need to use this function,
 * otherwise use readYaml to get Map directly).
 *
 * @param yaml - yaml ArrayList input.
 * @return - LazyMap.
 */
static Map yamlToLazyMap(def yaml) {
    return parseJson(new JsonBuilder(yaml).toPrettyString().replaceAll('^\\[', '').replaceAll('\\]$', '').stripIndent())
}

/**
 * Flatten nested map.
 *
 * @param sourceMap - source map,
 * @return - flatten map.
 */
static flattenNestedMap(Map sourceMap) {
    Map resultMap = [:]
    sourceMap.each {
        String paramPrefix = ''
        if (it.value instanceof Map) {
            paramPrefix = it.key.toString()
            it.value.each { k, v -> resultMap[String.format('%s_%s', paramPrefix, k)] = v }
        } else {
            resultMap[it.key] = it.value
        }
    }
    println String.format('flatten nested map results: \n%s', resultMap)
    return resultMap
}

/**
 * Run bash command via ssh on remote host.
 *
 * @param sshHostname - ssh host to connect via ssh.
 * @param sshUsername - ssh user.
 * @param sshPassword - ssh password.
 * @param returnStatus - return 'bash return code' when true.
 * @param returnStdout - return 'bash stdout' when true.
 * @param sshCommand - command to execute.
 * @return - bash return code in string format or bash stdout (see returnStatus).
 */
String runBashViaSsh(String sshHostname, String sshUsername, String sshPassword, Boolean returnStatus,
                     Boolean returnStdout, String sshCommand) {
    writeFile file: 'pass.txt', text: sshPassword
    String bashResult = sh(script: String.format("sshpass -f pass.txt ssh -q -o 'StrictHostKeyChecking no' %s@%s '%s'",
            sshUsername, sshHostname, sshCommand), returnStatus: returnStatus, returnStdout: returnStdout).toString()
    sh 'rm -f pass.txt'
    return bashResult
}


/**
 * Read all files content from the directory into map.
 *
 * @param path - path to read all files.
 * @param namePrefix - name prefix which will be added to map names.
 * @param namePostfix - name postfix which will be added to map postfix.
 * @return - map with files content.
 */
Map readFilesToMap(String path, String namePrefix, String namePostfix) {
    Map fileToMapResults = [:]
    try {
        dir(path) {
            List fileList = sh(returnStdout: true, script: 'ls -p ').trim().split('\n').toList()
            if (fileList) {
                fileList.each {
                    String index = it
                    if (it.find('.')) index = it.substring(0, it.lastIndexOf('.'))
                    String fileContent = readFile(it).trim()
                    fileToMapResults[String.format('%s%s%s', namePrefix,
                            index.replaceAll('[.-]', '_'), namePostfix)] = fileContent
                }
            }
        }
    } catch (Exception err) {
        outMsg(3, String.format('Unable to read files to Map from \'%s\': \n%s', path, readableError(err)))
    }
    return fileToMapResults
}


/**
 * Read all files content from the directory and sub-directories into a map.
 *
 * @param path - path to read all files in subdirectories.
 * @param namePrefix - name prefix which will be added to map names.
 * @param namePostfix - name postfix which will be added to map postfix.
 * @param excludeRegexp - regexp to exclude folders.
 * @return - map with files content.
 */
Map readSubdirectoriesToMap(String path, String namePrefix, String namePostfix, String excludeRegexp) {
    Map folderToMapResults = [:]
    // Here is some troubles when += null object to null map, so at least one map item with path
    folderToMapResults.valuestore_path = path
    try {
        dir(path) {
            List dirList = sh(returnStdout: true, script: 'for i in $(ls -d */); do echo ${i%%/}; done').trim()
                    .split('\n').toList()
            if (dirList)
                dirList.findAll { !it.matches(excludeRegexp) }.each {
                    Map folderContent = readFilesToMap(it.toString(), String.format('%s%s_', namePrefix, it),
                            namePostfix)
                    if (folderContent) folderToMapResults += folderContent
                }
        }
    } catch (Exception err) {
        outMsg(3, String.format('Unable to read \'%s\' directory to Map.\n%s', path, readableError(err)))
    }
    return folderToMapResults
}

/**
 * Find variables mentioning (e.g: $variable_name).
 *
 * @param text - text to scan,
 * @return - variables list.
 */
static ArrayList getVariablesMentioningFromString(String text) {
    return text.findAll('\\$[0-9a-zA-Z_]+').collect { it.replace('$', '') }
}

/**
 * Parse params map and replace $variables in every item of this map by items of replacement.
 *
 * These items should be placed in any items of params variable as a $variableName, that should be replace with value
 * from bindingValues. If there is no suitable value this item will be replaced with noDataBindingString (or skip if
 * noDataBindingString is null).
 *
 * @param params - current single regression params to be replaced. Every $variable will be replaced here.
 * @param bindingValues - single regression binding map (taken from file artifacts). Actually this is a map of
 *                        $variables to replace with.
 * @param noDataBindingString - replacement string for no binding result. Skipping replacement when null.
 * @return - regression messages map to send.
 */
Map replaceVariablesInMapItemsWithValues(Map params, Map bindingValues, String noDataBindingString) {
    Map messageMap = flattenNestedMap(params)
    ArrayList messageTemplateVariablesList = getVariablesMentioningFromString(messageMap.toString())
    Map resultsBinding = [:]
    String bindingLogMessage = 'replaceVariablesInMapItemsWithValues | Binding log:\n'
    messageTemplateVariablesList.each {
        if (bindingValues[it].find()) {
            bindingLogMessage += String.format('found: \'%s\': \n%s\n', it, bindingValues[it])
            resultsBinding[it] = bindingValues[it]
        } else {
            if (noDataBindingString != null) {
                bindingLogMessage += String.format('\'%s\' not found, replaced with: \'%s\'\n', it, noDataBindingString)
                resultsBinding[it] = noDataBindingString
            } else {
                bindingLogMessage += String.format('\'%s\' not found, leaving this unchanged: \'$%s\'\n', it, it)
                resultsBinding[it] = String.format('$%s', it)
            }
        }
    }
    outMsg(0, String.format('%s\n\n', bindingLogMessage))
    String templatedLogMessage = 'replaceVariablesInMapItemsWithValues | templated:\n'
    messageMap.each {
        String templatedValue = new StreamingTemplateEngine().createTemplate(it.value.toString()).make(resultsBinding)
                .toString()
        messageMap[it.key] = templatedValue
        templatedLogMessage += String.format('\'%s\': %s\n', messageMap[it.key], templatedValue)
    }
    outMsg(0, String.format('%s\n\n', templatedLogMessage))
    return messageMap
}

/**
 * Save Map to jenkins properties file.
 *
 * @param path - path and filename for properties file,
 * @param values - map with values to save.
 * @return - true when ok.
 */
Boolean saveMapToPropertiesFile(String path, Map values) {
    try {
        writeFile(file: path, text: values.collect {String.format("%s=%s", it.key, it.value) }.join('\n'))
        return true
    } catch (Exception err) {
        outMsg(3, String.format('Unable to save \'%s\' with values: \n%s\nbecause of:\n%s', path,
                readableMap(values), readableError(err)))
        return false
    }
}

/**
 * Check defined variables.
 * Check pipeline variables for current job are not empty. Stop with return code 1 or just print when one or more
 * variables wasn't defined.
 *
 * @param variableList - list of variable names to check.
 * @param variableValueList - list of variable values to check.
 * @param stopOnError - true: terminate job on error, false: just output an error.
 * @return - true when missing variable.
 */
Boolean checkRequiredVariables(List variableList, List variableValueList, Boolean stopOnError) {
    Boolean errorsFound = false
    for (int i = 0; i < variableList.size(); i++) {
        if (!variableValueList[i]) {
            errorsFound = true
            outMsg(3, String.format("%s is undefined for current job run", variableList[i]))
        }
    }
    if (errorsFound.toBoolean() && stopOnError.toBoolean())
        error 'Current job run terminated. Please specify the parameters listed above and run again.'
    return errorsFound
}

/**
 * Get filename and extension separately.
 *
 * @param filenameWithExtension - file name with extension.
 * @return - list with filename and extension.
 */
@NonCPS
static getFilenameExtension(String filenameWithExtension) {
    return [FilenameUtils.removeExtension(filenameWithExtension), FilenameUtils.getExtension(filenameWithExtension)]
}

/**
 * Detect archive type (tar.xx or zip) and extract.
 *
 * @param filenameWithExtension - file name with extension.
 * @return - true when success.
 */
Boolean extractArchive(String filenameWithExtension) {
    def (String filename, String extension) = getFilenameExtension(filenameWithExtension)
    outMsg(1, String.format('Got filename: %s, extension: %s', filename, extension))
    String extractScript = String.format('tar -xvf %s', filenameWithExtension)
    if (extension == 'zip') extractScript = String.format('unzip %s', filenameWithExtension)
    return (sh(returnStdout: true, returnStatus: true, script: extractScript) == 0)
}

/**
 * Clone GitLab or GitHub project into a directory.
 *
 * @param projectGitUrl - Git URL of the project to clone.
 * @param projectGitBranch - Git branch of the project.
 * @param projectLocalPath - Local path to clone into (e.g. 'subfolder'). Skip to clone into Jenkins workspace.
 * @param gitCredentialsId - Git credentials ID for git authorisation to clone project (something like
 *                           'a123b01c-456d-7890-ef01-2a34567890b1').
 * @param cleanBeforeCloning - cleanup directory before cloning.
 */
def cloneGitToFolder(String projectGitUrl, String projectGitlabBranch, String projectLocalPath = '',
                        String gitCredentialsId = OrgAlxGlobals.GitCredentialsID, Boolean cleanBeforeCloning = true) {
    dir(projectLocalPath) {
        if (cleanBeforeCloning) sh 'rm -rf ./*'
        git(branch: projectGitlabBranch, credentialsId: gitCredentialsId, url: projectGitUrl)
    }
}

/**
 * Install list of ansible-galaxy collections.
 *
 * @param ansibleGitUrl - Git URL of ansible project. Leave empty if your ansible collection already cloned to ./ansible
 *                        folder.
 * @param ansibleGitBranch - Git branch of ansible project to clone.
 * @param cleanupBeforeAnsibleClone - Clean-up folder before ansible clone.
 * @param gitCredentialsId - CredentialsID for git authorisation on ansible project clone.
 * @param ansibleCollections - List of ansible-galaxy collections to install, e.g: 'namespace.collection_name' that
 *                             should be placed inside 'ansible_collections' folder as ansible-galaxy standards.
 * @return - true when success.
 */
Boolean installAnsibleGalaxyCollections(String ansibleGitUrl, String ansibleGitBranch, List ansibleCollections,
                                        Boolean cleanupBeforeAnsibleClone = true,
                                        String gitCredentialsId = OrgAlxGlobals.GitCredentialsID) {
    Boolean ansibleGalaxyInstallOk = true
    if (ansibleGitUrl.trim())
        cloneGitToFolder(ansibleGitUrl, ansibleGitBranch, 'ansible', gitCredentialsId, cleanupBeforeAnsibleClone)
    ansibleCollections.each {
        dir(String.format('ansible/ansible_collections/%s', it.replace('.', '/'))) {
            ansibleGalaxyInstallOk = (sh(returnStdout: true, returnStatus: true,
                    script: String.format('''ansible-galaxy collection build 
                            ansible-galaxy collection install $(ls -1 | grep "%s" | grep ".tar.gz") -f''',
                            it.replace('.', '-'))) != 0) ? false : ansibleGalaxyInstallOk
            if (!ansibleGalaxyInstallOk)
                outMsg(3, String.format('There was an error building and installing %s ansible collection.', it))
        }
    }
    return ansibleGalaxyInstallOk
}

/**
 * Run ansible role/playbook with (optional) ansible-galaxy collections install.
 *
 * @param ansiblePlaybookText - text content of ansible playbook/role.
 * @param ansibleInventoryText - text content of ansible inventory file.
 * @param ansibleGitUrl - (optional) Git URL of ansible project to clone and run. Leave empty for ansible collection
 *                        mode, but skip collection cloning and install.
 * @param ansibleGitBranch - (optional) gitlab branch of ansible project.
 * @param ansibleExtras - (optional) extra params for playbook running.
 * @param ansibleCollections - (optional) list of ansible-galaxy collections dependencies which will be installed before
 *                             running the script. Collections should be placed in ansible gitlab project according to
 *                             ansible-galaxy directory layout standards. If variable wasn't pass (empty) the roles
 *                             will be called an old-way from a playbook placed in 'roles/execute.yml'. It's only for
 *                             the backward capability.
 * @param ansibleInstallation - name of the ansible installation predefined in jenkins Global Configuration Tool.
 *                              Check https://issues.jenkins.io/browse/JENKINS-67209 for details.
 * @param cleanupBeforeAnsibleClone - Clean-up folder before ansible git clone.
 * @param gitCredentialsId - Git credentialsID to clone ansible project.
 * @return - success (true when ok).
 */
Boolean runAnsible(String ansiblePlaybookText, String ansibleInventoryText, String ansibleGitUrl = '',
                   String ansibleGitBranch = 'main', String ansibleExtras = '', List ansibleCollections = [],
                   String ansibleInstallation = OrgAlxGlobals.AnsibleInstallationName,
                   Boolean cleanupBeforeAnsibleClone = true, String gitCredentialsId = OrgAlxGlobals.GitCredentialsID) {
    Boolean runAnsibleState = true
    String ansiblePlaybookPath = 'ansible'
    try {
        if (ansibleCollections) {
            if (ansibleGitUrl?.trim()) {
                runAnsibleState = installAnsibleGalaxyCollections(ansibleGitUrl, ansibleGitBranch, ansibleCollections,
                        cleanupBeforeAnsibleClone, gitCredentialsId)
                if (!runAnsibleState) return false
            }

        } else {
            ansiblePlaybookPath += '/roles'
        }
        dir(ansiblePlaybookPath) {
            writeFile file: 'inventory.ini', text: ansibleInventoryText
            writeFile file: 'execute.yml', text: ansiblePlaybookText
            String ansibleMode = String.format('ansible%s', ansiblePlaybookPath == 'ansible' ?:
                    String.format(' collection(s)', ansibleCollections.toString()))
            outMsg(1, String.format('Running %s from:\n%s\n%s', ansibleMode, ansiblePlaybookText, ("-" * 32)))
            wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']) {
                ansiblePlaybook(playbook: 'execute.yml', inventory: 'inventory.ini', installation: ansibleInstallation,
                        colorized: true, extras: ansibleExtras)
            }
        }
    } catch (Exception err) {
        outMsg(3, String.format('Running ansible failed: %s', readableError(err)))
        runAnsibleState = false
    } finally {
        sh String.format('rm -f %s/inventory.ini || true', ansiblePlaybookPath)
        return runAnsibleState
    }
}

/**
 * Clean SSH hosts fingerprints from ~/.ssh/known_hosts.
 *
 * @param hostsToClean - IPs, FQCNs or dns names list to clean.
 */
def cleanSshHostsFingerprints(ArrayList hostsToClean) {
    hostsToClean.findAll { it }.each {
        ArrayList items = (it.matches('^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$')) ? [it] : [it] + sh(script:
                String.format('getent hosts %s | cut -d\' \' -f1', it), returnStdout: true).split('\n').toList()
        items.each { host ->
            if (host?.trim()) sh String.format('ssh-keygen -f "${HOME}/.ssh/known_hosts" -R %s', host)
        }
    }
}

/**
 * Handle jenkins downstream jobs in dry-run mode.
 *
 * @param jobName - job or jenkins pipeline name.
 * @param jobParams - job or pipeline parameters.
 * @param dryRun - dry run mode enabled.
 * @param runJobWithDryRunParam - (optional) run job or pipeline with additional enabled DRY_RUN parameter, otherwise
 *                                do not run and print the message.
 * @param propagateErrors - (optional) propagate job or pipeline errors.
 * @param waitForComplete - (optional) wait for completion.
 * @param envVariables - (optional) env variables (env which is class org.jenkinsci.plugins.workflow.cps.EnvActionImpl).
 * @param dryRunEnvVariableName - (optional) environment variable name of dry-run switch.
 * @param printableJobParams - (optional) just printable
 * @return - run wrapper of current job or pipeline build, or null for skipped run.
 */
def dryRunJenkinsJob(String jobName, ArrayList jobParams, Boolean dryRun, Boolean runJobWithDryRunParam = false,
                     Boolean propagateErrors = true, Boolean waitForComplete = true, Object envVariables = env,
                     String dryRunEnvVariableName = 'DRY_RUN', ArrayList printableJobParams = []) {
    if (runJobWithDryRunParam)
        jobParams += [booleanParam(name: dryRunEnvVariableName, value: envVariables.getEnvironment()
                .get(dryRunEnvVariableName)?.toBoolean())]
    if (dryRun)
        outMsg(2, String.format("Dry-run mode. Run '%s': %s. Job/pipeline parameters:\n%s", jobName,
                runJobWithDryRunParam, readableJobParams(printableJobParams.size() ? printableJobParams : jobParams)))
    if (!dryRun || runJobWithDryRunParam) {
        return build(job: jobName, parameters: jobParams, propagate: propagateErrors, wait: waitForComplete)
    } else {
        outMsg(2, String.format("%s '%s' %s%s), no job results.", 'Dry-run mode. Running', jobName,
                'was skipped (runJobWithDryRunParam=', runJobWithDryRunParam))
        return null
    }
}

/** Serialize environment variables into map
 *
 * @param envVars - environment variables.
 * @return - map with environment variables.
 */
static envVarsToMap(envVars) {
    Map envVarsMap = [:]
    envVars.getEnvironment().each { name, value -> envVarsMap.put(name, value) }
    return envVarsMap
}

/**
 * Rename Map key names with regex.
 *
 * @param sourceMap - source map.
 * @param regex - regex string.
 * @return - result map.
 */
static regexMapKeyNames(Map sourceMap, String regex) {
    Map resultMap = [:]
    sourceMap.each { name, value -> resultMap.put(name.replaceAll(regex, ''), value) }
    return resultMap
}

/** Fix data typing of map values.
 * (If source map is string with 'true', 'false', '1212121' etc)
 *
 * @param sourceMap - source map.
 * @return - map with typed values.
 */
static fixMapValuesDataTyping(Map sourceMap) {
    Map resultMap = [:]
    sourceMap.each { name, value ->
        if (value instanceof String && (value == 'true' || value == 'false')) {
            resultMap.put(name, value.toBoolean())
        } else if (value instanceof String && value.matches('\\d+')) {
            resultMap.put(name, value.toInteger())
        } else {
            resultMap.put(name, value)
        }
    }
    return resultMap
}

/**
 * Wait ssh host up/down.
 *
 * @param sshHostname - ssh hostname or IP.
 * @param sshUsername - username for ssh connection.
 * @param sshPassword - user password for ssh connection.
 * @param sshHostUp - wait ssh host up when true, or down when false.
 * @param timeOut - timeout (minutes).
 * @param jenkinsHomeFolder - jenkins home folder path.
 * @return - true when success.
 */
Boolean waitSshHost(String sshHostname, String sshUsername, String sshPassword, Boolean sshHostUp = true,
                    Integer timeOut = OrgAlxGlobals.WaitSshHostUpDownTimeout,
                    String jenkinsHomeFolder = OrgAlxGlobals.JenkinsUserDefaultHomeFolder) {
    try {
        sh String.format("ssh-keygen -f '%s/.ssh/known_hosts' -R %s", jenkinsHomeFolder, sshHostname)
        timeout(timeOut) {
            waitUntil {
                script {
                    Boolean r = (runBashViaSsh(sshHostname, sshUsername, sshPassword, true, false,
                            'exit').toInteger() == 0)
                    if (!sshHostUp) r = !r
                    return r
                }
            }
        }
    } catch (ignored) {
        outMsg(3, String.format('Waiting %s ssh %s failed.', sshHostname, (sshHostUp ? 'up' : 'down')))
        return false
    }
    return true
}

/**
 * Run scp (copy via ssh).
 *
 * @param sshHostname - ssh host to connect via ssh.
 * @param sshUsername - ssh user.
 * @param sshPassword - ssh password.
 * @param sourcePath - source path to copy from.
 * @param destinationPath - destination path.
 * @param returnStatus - return 'bash return code' when true.
 * @param returnStdout - return 'bash stdout' when true.
 * @param scpDirection - true when scp to remote, false when scp from remote.
 * @return - bash return code in string format or bash stdout (see returnStatus).
 */
String runBashScp(String sshHostname, String sshUsername, String sshPassword, String sourcePath, String destinationPath,
                  Boolean returnStatus = true, Boolean returnStdout = false, Boolean scpDirection = true) {
    writeFile file: 'pass.txt', text: sshPassword
    String scpPathArgs = String.format('%s@%s:%s %s', sshUsername, sshHostname, sourcePath, destinationPath)
    if (scpDirection)
        scpPathArgs = String.format('%s %s@%s:%s', sourcePath, sshUsername, sshHostname, destinationPath)
    String bashResulting = sh(script: String.format("sshpass -f pass.txt scp -ro 'StrictHostKeyChecking no' %s",
            scpPathArgs), returnStatus: returnStatus, returnStdout: returnStdout).toString()
    sh 'rm -f pass.txt'
    return bashResulting
}

/**
 * Interrupt pipeline execution with "SUCCESS" build results.
 *
 * @param sleepSeconds - sleep seconds to wait finishing up.
 */
def interruptPipelineOk(Integer sleepSeconds = 2) {
    currentBuild.build().getExecutor().interrupt(Result.SUCCESS)
    sleep(time: sleepSeconds, unit: "SECONDS")
}

/**
 * Get all jenkins nodes and filter them by label mask or name mask.
 *
 * @param filterMask - string that should be contained for include to results.
 * @param filterByLabel - when true filter by node label, otherwise filter by node name.
 * @return - list of nodes.
 */
ArrayList getJenkinsNodes(String filterMask = '', Boolean filterByLabel = false) {
    Object jenkinsComputers = jenkins.model.Jenkins.get().computers
    if (!filterMask.trim())
        return jenkinsComputers.collect { it.node.selfLabel.name }
    if (!filterByLabel)
        return jenkinsComputers.findAll { it.node.selfLabel.name.contains(filterMask) }.collect {
            it.node.selfLabel.name }
    ArrayList nodes = []
    jenkinsComputers.each {
        if (it.node.labelString.contains(filterMask)) nodes.add(it.node.selfLabel.name)
    }
    return nodes
}

/**
 * Make list of enabled options from status map.
 *
 * @param optionsMap - Map with item status and description, e.g:
 *                     [option_1: [state: true, description: 'text_1'],
 *                     option_2: [state: false, description: 'text_2']].
 * @param formatTemplate - String format template, e.g: '%s - %s' (where the first is name, second is description).
 * @return - list of [enabled options list, descriptions of enabled options list].
 */
static makeListOfEnabledOptions(Map optionsMap, String formatTemplate = '%s - %s') {
    ArrayList options = []
    ArrayList descriptions = []
    optionsMap.each {
        if (it.value.get('state')) {
            options.add(it.key)
            if (it.value.get('description'))
                descriptions.add(String.format(formatTemplate, it.key, it.value.description))
        }
    }
    return [options, descriptions]
}

/**
 * Grep only specified states from stages status list.
 *
 * @param states - map including key with these states steps.
 * @param inputKeyName - key name in this map to get failed states from.
 * @param grepString - string pattern that should be contained to grep.
 * @return - string of failed states list.
 */
@NonCPS
static grepFailedStates(Map states, String inputKeyName, String grepString = '[FAILED]') {
    return (states.find{ it.key == inputKeyName }?.value) ? states[inputKeyName].readLines()
            .grep { it.contains(grepString) }.join('\n') : ''
}

