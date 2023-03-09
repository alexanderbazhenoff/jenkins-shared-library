# jenkins shared library

Some common things that makes writing Jenkins pipelines easier.

### Usage

1. Connect this repository to your Jenkins shared library as described in
[`official documentation`](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#global-shared-libraries).
2. Write your Jenkins pipeline like:

```groovy
#!/usr/bin/env groovy

@Library('jenkins-shared-library') _

node(master) {
    CommonFunctions = new org.alx.commonFunctions() as Object
    
    // your pipeline code and call this object by 'CommonFunctions.<functionName>', e.g:
    CommonFunctions.cleanSshHostsFingerprints(env.IP_LIST.split(' ').toList())
    // where IP_LIST is a space separated string IP list which is defined by pipeline 
    // parameter IP_LIST
}
```
3. Read `Groovydoc` in the comments of file(s), e.g:
[`src/org/alx/commonFunctions.groovy`](src/org/alx/commonFunctions.groovy) for more details.
