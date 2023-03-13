import hudson.FilePath
import hudson.model.ParametersAction
import hudson.model.FileParameterValue
import hudson.model.Executor

/**
 * Unstash file parameter from jenkins pipeline.
 *
 * This pipeline library contains a workaround for Jenkins issue: https://issues.jenkins-ci.org/browse/JENKINS-27413
 * It provides a unstashParameter that saves file parameter to a workspace.
 *
 * @param name - parameter name,
 * @param fname - (optional) input filename.
 * @return - stashed filename.
 */
def call(String name, String fname = null) {
    def paramsAction = currentBuild.rawBuild.getAction(ParametersAction.class)
    if (paramsAction) {
        for (param in paramsAction.getParameters()) {
            if (param.getName().equals(name)) {
                if (! (param instanceof FileParameterValue)) error String.format('Not a file parameter: %s', name)
                if (!param.getOriginalFileName()) error 'File was not uploaded'
                if (!env.NODE_NAME) error 'No node in current context'
                if (!env.WORKSPACE) error 'No workspace in current context'
                if (env.NODE_NAME == 'master' || env.NODE_NAME == 'built-in') {
                    workspace = new FilePath(null, env.WORKSPACE)
                } else {
                    workspace = new FilePath(Jenkins.getInstance().getComputer(env.NODE_NAME).getChannel(),
                            env.WORKSPACE)
                }
                filename = fname == null ? param.getOriginalFileName() : fname
                file = workspace.child(filename)
                destinationFolder = file.getParent()
                destinationFolder.mkdirs()
                file.copyFrom(param.getFile())
                return filename
            }
        }
    }
    error String.format('No file parameter named \'%s\'', name)
}
