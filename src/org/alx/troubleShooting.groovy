package org.alx


// https://stackoverflow.com/questions/54125894/jenkins-shared-library-src-class-unable-to-resolve-class
class OrgAlxGlob {
    public static String GitCredentialsID = '123'

    static sayHi() {
        return String.format("Hello, %s which is %s.", GitCredentialsID, GitCredentialsID.getClass())
    }

}

static TestFunctionInSrc(String cred = OrgAlxGlob.GitCredentialsID) {
    return [String.format('src: %s', cred), OrgAlxGlob.sayHi().getClass(), OrgAlxGlob.sayHi()]
}
