
# MaxRAMPercentage will default to 25 if not set ... check using:
#   kubectl exec -c <container> <pod> -- java -XX:+PrintFlagsFinal -version | grep MaxRAMPercentage
# (an alternative is to set Xmx and Xms)
# 75 is a good starting point, but you might set it even higher

DEFAULT_JVM_OPTS="${DEFAULT_JVM_OPTS} -XX:MaxRAMPercentage=75"

# No of processors, check what the JVM sees using:
#   kubectl exec -c <container> <pod> -- bash -c 'echo "System.out.println(Runtime.getRuntime().availableProcessors());"|jshell -s - 2>/dev/null' 2>/dev/null
# If you have CPU limit =< 1000m set ActiveProcessorCount to 2

DEFAULT_JVM_OPTS="${DEFAULT_JVM_OPTS} -XX:ActiveProcessorCount=2"

# Beware that you're app might experience throttling if you have low CPU-limits

export DEFAULT_JVM_OPTS
