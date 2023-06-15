package org.alx


// https://stackoverflow.com/questions/54125894/jenkins-shared-library-src-class-unable-to-resolve-class
class OrgAlxGlob implements Serializable {
    public String GitCredentialsID = '123'

    OrgAlxGlob(String pName) {
        this.GitCredentialsID = pName
    }

    def sayHi() {
        return String.format("Hello, %s which is %s.", this.GitCredentialsID, this.GitCredentialsID.getClass())
    }

    def sayHi(String name) {
        return String.format("Hello, %s which is %s.", name, name.getClass())
    }
}

static TestFunctionInSrc(String cred = OrgAlxGlob.GitCredentialsID) {
    return [String.format('src: %s', cred), OrgAlxGlob.getClass(), OrgAlxGlob.sayHi]
}
