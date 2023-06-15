package org.alx


// https://stackoverflow.com/questions/54125894/jenkins-shared-library-src-class-unable-to-resolve-class
class OrgAlxGlob implements Serializable {
    public static String GitCredentialsID = ''
}

static TestFunctionInSrc(String cred = OrgAlxGlob.GitCredentialsID) {
    return [String.format('src: %s', cred), cred.getClass(), OrgAlxGlob.GitCredentialsID.getClass()]
}
