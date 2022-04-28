package org.apache.james.gatling.simulation

import java.net.URL

import scala.concurrent.duration._
import scala.util.Properties

object Configuration {

  val ServerHostName = Properties.envOrElse("TARGET_HOSTNAME", "james-admin.upn.integration-open-paas.org")
  val JmapServerHostName = "jmap.upn.integration-open-paas.org"
  val ImapServerHostName = "imap.upn.integration-open-paas.org"
  val WebadminServerHostName = Properties.envOrElse("WEBADMIN_SERVER_HOSTNAME", ServerHostName)

  val JMAP_PORT = Properties.envOrElse("JMAP_PORT", "443").toInt
  val JMAP_PROTOCOL = Properties.envOrElse("JMAP_PROTOCOL", "https")
  val WS_PROTOCOL = Properties.envOrElse("WS_PROTOCOL", "wss")
  val WS_PORT = Properties.envOrElse("WS_PORT", String.valueOf(JMAP_PORT)).toInt
  val BaseJmapUrl = new URL(s"$JMAP_PROTOCOL://$JmapServerHostName:$JMAP_PORT")
  val BaseWsUrl = s"$WS_PROTOCOL://$JmapServerHostName:$WS_PORT"

  val WEBADMIN_PORT = Properties.envOrElse("WEBADMIN_PORT", "8000").toInt
  val WEBADMIN_PROTOCOL = Properties.envOrElse("WEBADMIN_PROTOCOL", "http")
  val BaseJamesWebAdministrationUrl = new URL(s"$WEBADMIN_PROTOCOL://$WebadminServerHostName")

  val DURATION_PROPERTY = Properties.envOrNone("DURATION") match {
    case Some(duration) => Some(duration.toInt minutes)
    case _ => None
  }

  val ScenarioDuration = DURATION_PROPERTY.getOrElse(12 minutes)
  val InjectionDuration = DURATION_PROPERTY.getOrElse(8 minutes)
  val UserCount = 1000
  val RandomlySentMails = 10
  val NumberOfMailboxes = 10
  val NumberOfMessages = 20

}
