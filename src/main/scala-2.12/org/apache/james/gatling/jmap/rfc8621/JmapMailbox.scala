package org.apache.james.gatling.jmap.rfc8621

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gatling.http.check.HttpCheck
import io.gatling.http.request.builder.HttpRequestBuilder
import org.apache.james.gatling.control.RecipientFeeder.RecipientFeederBuilder

import scala.concurrent.duration._

object JmapMailbox {
  private val loopVariableName = "any"

  def getMailboxes: HttpRequestBuilder =
      JmapHttp.apiCall("getMailboxes")
      .body(StringBody(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |    "Mailbox/get",
           |    {
           |      "accountId": "$${accountId}",
           |      "ids": null
           |    },
           |    "c1"]]
           |}""".stripMargin))

  private val mailboxListPath = "$.methodResponses[0][1].list"
  private val inboxIdPath = s"$mailboxListPath[?(@.role == 'inbox')].id"
  private val outboxIdPath = s"$mailboxListPath[?(@.role == 'outbox')].id"
  private val sentIdPath = s"$mailboxListPath[?(@.role == 'sent')].id"
  private val draftsIdPath = s"$mailboxListPath[?(@.role == 'drafts')].id"
  private val trashIdPath = s"$mailboxListPath[?(@.role == 'trash')].id"
  private val spamIdPath = s"$mailboxListPath[?(@.role == 'spam')].id"

  val getSystemMailboxesChecks: Seq[HttpCheck] = Seq[HttpCheck](JmapHttp.statusOk, JmapHttp.noError) ++
    Seq[HttpCheck](
      jsonPath(inboxIdPath).saveAs("inboxMailboxId"),
      jsonPath(outboxIdPath).saveAs("outboxMailboxId"),
      jsonPath(sentIdPath).saveAs("sentMailboxId"),
      jsonPath(draftsIdPath).saveAs("draftMailboxId"),
      jsonPath(trashIdPath).saveAs("trashMailboxId"),
      jsonPath(spamIdPath).saveAs("spamMailboxId"))

  private def mailboxesIdPathForMailboxesWithAtLeastMessages(nbMessages : Int) = s"$mailboxListPath[?(@.totalEmails >= $nbMessages)].id"

  def saveInboxAs(key: String): HttpCheck = jsonPath(inboxIdPath).saveAs(key)

  def saveRandomMailboxWithAtLeastMessagesAs(key: String, atLeastMessages : Int): HttpCheck =
    jsonPath(mailboxesIdPathForMailboxesWithAtLeastMessages(atLeastMessages))
      .findRandom
      .saveAs(key)

  def provisionSystemMailboxes(): ChainBuilder =
    exec(JmapMailbox.getMailboxes
      .check(getSystemMailboxesChecks: _*))
      .pause(1 second)

  def provisionUsersWithMessages(recipientFeeder: RecipientFeederBuilder, numberOfMessages: Int): ChainBuilder =
    exec(provisionSystemMailboxes())
      .repeat(numberOfMessages, loopVariableName) {
        exec(JmapEmail.submitEmails(recipientFeeder))
          .pause(1 second, 2 seconds)
      }
      .pause(5 second)
}