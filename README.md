# JMX opener

Simple command line application to open jmx application on a specific jvm process.


Usage:

```
java -cp jmx-opener.jar:/usr/lib/jvm/java-1.8.0/lib/tools.jar net.anzix.jmxopener.Starter NodeManager 20002
```

The first parameters is pid _or_ pattern, the second parameter is the jmx port to open.
