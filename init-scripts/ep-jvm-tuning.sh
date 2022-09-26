
# MaxRAMPercentage will default to 25 if not set ... check using:
#   kubectl exec -c <container> <pod> -- java -XX:+PrintFlagsFinal -version | grep MaxRAMPercentage
# (an alternative is to set Xmx and Xms)
# 75 is a good starting point, but you might set it even higher
DEFAULT_JVM_OPTS="${DEFAULT_JVM_OPTS} -XX:MaxRAMPercentage=75"

export DEFAULT_JVM_OPTS
