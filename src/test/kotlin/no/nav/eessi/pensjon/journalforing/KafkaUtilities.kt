package no.nav.eessi.pensjon.journalforing

import org.apache.kafka.common.acl.AccessControlEntry
import org.apache.kafka.common.acl.AclBinding
import org.apache.kafka.common.acl.AclOperation
import org.apache.kafka.common.acl.AclPermissionType
import org.apache.kafka.common.resource.PatternType
import org.apache.kafka.common.resource.ResourcePattern
import org.apache.kafka.common.resource.ResourceType

/* See: https://github.com/navikt/kafka-embedded-env/blob/master/src/test/kotlin/no/nav/common/test/common/Utilities.kt */
fun createProducerACL(topicUser: Map<String, String>): List<AclBinding> =
        topicUser.flatMap {
            val (topic, user) = it

            listOf(AclOperation.DESCRIBE, AclOperation.WRITE, AclOperation.CREATE).let { lOp ->

                val tPattern = ResourcePattern(ResourceType.TOPIC, topic, PatternType.LITERAL)
                val principal = "User:$user"
                val host = "*"
                val allow = AclPermissionType.ALLOW

                lOp.map { op -> AclBinding(tPattern, AccessControlEntry(principal, host, op, allow)) }
            }
        }

/* See: https://github.com/navikt/kafka-embedded-env/blob/master/src/test/kotlin/no/nav/common/test/common/Utilities.kt */
fun createConsumerACL(topicUser: Map<String, String>): List<AclBinding> =
        topicUser.flatMap {
            val (topic, user) = it

            listOf(AclOperation.DESCRIBE, AclOperation.READ).let { lOp ->

                val tPattern = ResourcePattern(ResourceType.TOPIC, topic, PatternType.LITERAL)
                val gPattern = ResourcePattern(ResourceType.GROUP, "*", PatternType.LITERAL)
                val principal = "User:$user"
                val host = "*"
                val allow = AclPermissionType.ALLOW

                lOp.map { op -> AclBinding(tPattern, AccessControlEntry(principal, host, op, allow)) } +
                        AclBinding(gPattern, AccessControlEntry(principal, host, AclOperation.READ, allow))
            }
        }
