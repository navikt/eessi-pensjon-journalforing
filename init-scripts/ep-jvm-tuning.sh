
# MaxRAMPercentage will default to 25 if not set ... check using:
#   kubectl exec -c <container> <pod> -- java -XX:+PrintFlagsFinal -version | grep MaxRAMPercentage
# (an alternative is to set Xmx and Xms)
# 75 is a good starting point, but you might set it even higher

DEFAULT_JVM_OPTS="${DEFAULT_JVM_OPTS} -XX:MaxRAMPercentage=85"

# No of processors, check what the JVM sees using:
#   kubectl exec -c <container> <pod> -- bash -c 'echo "System.out.println(Runtime.getRuntime().availableProcessors());"|jshell -s - 2>/dev/null' 2>/dev/null
# If you have CPU limit =< 1000m set ActiveProcessorCount to 2

DEFAULT_JVM_OPTS="${DEFAULT_JVM_OPTS} -XX:ActiveProcessorCount=2"

# Beware that you're app might experience throttling if you have low CPU-limits

# G1GC is default on heaps > 1792MB if 2+ CPUs, otherwise SerialGC is used, check DEFAULT using:
#   kubectl exec -c <container> <pod> -- java -XX:+PrintFlagsFinal -version | egrep "UseG1GC|UseParallelGC|UseSerialGC|UseShenandoahGC|UseZGC"
# However, ParallelGC outperforms G1GC and SerialGC on smaller heaps (ParallelGC has Stop The World, but gives better throughput)
# (On large heaps / many processors there are other options, like ZGC and ShenandoahGC)
# On heaps < 2GB choose Parallel GC. On heaps 2-4GB you might get better throughput with G1GC, but ParallelGC is usually better
# DEFAULT_JVM_OPTS="${DEFAULT_JVM_OPTS} -XX:+UseParallelGC"
# On heaps over 4GB choose G1GC
# DEFAULT_JVM_OPTS="${DEFAULT_JVM_OPTS} -XX:+UseG1GC"
# However ... it is easier to see actual memory need using G1GC as it releases memory faster, and keeps memory use more in check (our experience)

DEFAULT_JVM_OPTS="${DEFAULT_JVM_OPTS} -XX:+UseG1GC"

export DEFAULT_JVM_OPTS
