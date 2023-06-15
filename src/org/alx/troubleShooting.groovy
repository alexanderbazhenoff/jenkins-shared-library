package org.alx


class OrgAlxGlob implements Serializable {
    public static String GitCredentialsID = ''
}

static TestFunctionInSrc(String cred = OrgAlxGlob.GitCredentialsID) {
    return [String.format('src: %s', cred), cred.getClass(), OrgAlxGlob.GitCredentialsID.getClass()]
}
