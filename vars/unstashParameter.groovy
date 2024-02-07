import hudson.FilePath
import hudson.model.ParametersAction
import hudson.model.FileParameterValue


/**
 * Unstash file parameter from jenkins pipeline.
 *
 * This pipeline library contains a workaround for Jenkins issue: https://issues.jenkins-ci.org/browse/JENKINS-27413
 * It provides a unstashParameter that saves file parameter to a workspace.
 *
 * @param paramName - Jenkins parameter name with uploaded file.
 * @param filename - Required filename of uploaded file. Do not define when any filename available to upload.
 * @return - Actual filename of uploaded file.
 */
String call(String paramName, String filename = null) {
    /* groovylint-disable UnnecessaryGetter */
    Object paramsAction = currentBuild.rawBuild.getAction(ParametersAction)
    if (paramsAction) {
        for (param in paramsAction.getParameters()) {
            if (param.getName() == paramName) {
                if (!(param instanceof FileParameterValue))
                    error String.format("'%s' is not a file parameter.", paramName)
                if (!param.getOriginalFileName()) error 'File was not uploaded.'
                if (!env.NODE_NAME) error 'No node in current context.'
                if (!env.WORKSPACE) error 'No workspace in current context.'
                FilePath workspace = new FilePath((env.NODE_NAME == 'master' || env.NODE_NAME == 'built-in') ?
                        null : Jenkins.getInstance().getComputer(env.NODE_NAME).getChannel(), env.WORKSPACE)
                String filenameFound = filename == null ? param.getOriginalFileName() : filename
                FilePath file = workspace.child(filenameFound)
                FilePath destFolder = file.getParent()
                destFolder.mkdirs()
                file.copyFrom(param.getFile())
                return filenameFound
                /* groovylint-enable UnnecessaryGetter */
            }
        }
    }
    error String.format("No file parameter named '%s'.", paramName)
}
