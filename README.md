# jenkins shared library

Some common things that makes writing Jenkins pipelines easier.

![lint](https://github.com/alexanderbazhenoff/jenkins-shared-library/actions/workflows/lint.yml/badge.svg?branch=main)

## Usage

1. Connect this repository to your Jenkins shared library as described in
[`official documentation`](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#global-shared-libraries).
2. Write your Jenkins pipeline like:

    **To use library from** [`src/org`](src/org/alx):

    ```groovy
    #!/usr/bin/env groovy
    
    @Library('jenkins-shared-library')
    
    node('master') {
        // To use methods and functions from commonFunctions.groovy:
        CommonFunctions = new org.alx.commonFunctions() as Object

        // And/or use methods from commonMethods.groovy:
        CommonMethods = new org.alx.commonMethods() as Object
        
        // your pipeline code and call this object by 'CommonFunctions.<functionName>', e.g:
        CommonFunctions.cleanSshHostsFingerprints(env.IP_LIST.tokenize(' '))
        // where IP_LIST is a space separated string IP list which is defined by pipeline 
        // parameter IP_LIST

        // For example, replace words 'ONE', 'TWO' and 'THREE' with similar digits in a string
        String text = 'This is some text containing ONE word and TWO word. May bet the word number THREE.'
        List regexList = ['ONE', 'TWO', 'THREE']
        List replacementList = ['1', '2', '3']
        Strin resultingText = CommonMethods.applyReplaceRegexItems(text, regexList, replacementList)
        println resultingText
        // Output:
        // This is some text containing 1 word and 2 word. May bet the word number 3.
    }
    ```

    To use GitLab related functions (e.g. runAnsible) you should set GitCredentialsID variable, which can be defined in
`OrgAlxGlobals()` class:

    ```groovy
    @Library('jenkins-shared-library')
   
    node('master') {
        CommonFunctions = new org.alx.commonFunctions() as Object  // shout be placed before GlobalConstants 
        GlobalConstants = new org.alx.OrgAlxGlobals() as Object
        // Then use constants from OrgAlxGlobals        
    }
    ```

    **To use library from** [`vars`](vars):

    ```groovy
    @Library('jenkins-shared-library')
    // or @Library(['jenkins-shared-library', 'another-library']) _
    // if you need to use several libraries.
    
    node('master') {
        // your pipeline code then example usage of unstashParameter:
        String fileInWorkspace = unstashParameter "JENKINS_PIPELINE_FILE_PARAMETER_NAME"
        // Output file content
        sh String.format('cat %s', fileInWorkspace)
    }
    
    ```

    Please also keep in mind a differences between `vars` and `src` folder organizations
    (read [**this article**](http://tdongsi.github.io/blog/2017/12/26/class-in-jenkins-shared-library/) for details).

3. Read `Groovydoc` in the comments of file(s) (e.g:
[`src/org/alx/commonFunctions.groovy`](src/org/alx/commonFunctions.groovy)) for details.
4. (Optional) To use Git-related functions (e.g. `cloneGitToFolder()`, `installAnsibleGalaxyCollections()` or
`runAnsible()`) to clone from ssh URL you should set up **GitCredentialsID** variable in
[`commonFunctions.groovy`](src/org/alx/commonFunctions.groovy).
