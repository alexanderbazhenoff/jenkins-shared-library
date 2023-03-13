# jenkins shared library

Some common things that makes writing Jenkins pipelines easier.

### Usage

1. Connect this repository to your Jenkins shared library as described in
[`official documentation`](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#global-shared-libraries).
2. Write your Jenkins pipeline like:

#### To use library from [`src/org`](src/org/alx):

```groovy
#!/usr/bin/env groovy

@Library('jenkins-shared-library') _

node('master') {
    CommonFunctions = new org.alx.commonFunctions() as Object
    
    // your pipeline code and call this object by 'CommonFunctions.<functionName>', e.g:
    CommonFunctions.cleanSshHostsFingerprints(env.IP_LIST.split(' ').toList())
    // where IP_LIST is a space separated string IP list which is defined by pipeline 
    // parameter IP_LIST
}
```

#### To use library from [`vars`](vars):

```groovy
@Library('jenkins-shared-library') _

node('master') {
    // your pipeline code then example usage of unstashParameter:
    String file_in_workspace = unstashParameter "SOME_JENKINS_PARAMETER_NAME"
    // Output file content
    sh String.format('cat %s', file_in_workspace)
}

```

Please also keep in mind a differences between `vars` and `src` folder organizations 
(read [article](http://tdongsi.github.io/blog/2017/12/26/class-in-jenkins-shared-library/)).

3. Read `Groovydoc` in the comments of file(s) (e.g:
[`src/org/alx/commonFunctions.groovy`](src/org/alx/commonFunctions.groovy)) for details.
