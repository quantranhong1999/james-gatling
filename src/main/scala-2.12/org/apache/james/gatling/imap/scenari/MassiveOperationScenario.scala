package org.apache.james.gatling.imap.scenari

import java.util.Calendar

import com.linagora.gatling.imap.PreDef.{imap, ok}
import com.linagora.gatling.imap.protocol.command.MessageRange.From
import com.linagora.gatling.imap.protocol.command.{MessageRanges, Silent, StoreFlags}
import io.gatling.core.Predef._
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure._

import scala.collection.immutable.Seq
import scala.concurrent.duration._

class MassiveOperationScenario {
  private val gracePeriod = 5 milliseconds
  private val numberOfMailInInbox = 12000
  private val numberOfSubMailboxes = 1000
  private val mailboxName = "Mailbox"
  private val mailboxAName = mailboxName + "A"
  private val mailboxBName = mailboxName + "B"
  private val mailboxCName = mailboxName + "C"
  private val mailboxXName = mailboxName + "X"
  private val mailboxYName = mailboxName + "Y"
  private val mailboxZName = mailboxName + "Z"
  private val mailboxTName = mailboxName + "T"
  private val mailboxDName = mailboxName + "D"
  private val mailboxDNewName = mailboxName+ "Dbis"

  private val createMailboxA_B_C_X_Y_Z_T = exec(imap("createFolder").createFolder(mailboxAName).check(ok))
    .exec(imap("createFolder").createFolder(mailboxBName).check(ok))
    .exec(imap("createFolder").createFolder(mailboxCName).check(ok))
    .exec(imap("createFolder").createFolder(mailboxXName).check(ok))
    .exec(imap("createFolder").createFolder(mailboxYName).check(ok))
    .exec(imap("createFolder").createFolder(mailboxYName).check(ok))
    .exec(imap("createFolder").createFolder(mailboxZName).check(ok))
    .exec(imap("createFolder").createFolder(mailboxTName).check(ok))

  private val populateMailboxA = repeat(numberOfMailInInbox)(pause(gracePeriod)
    .exec(imap("append").append(mailboxAName, Option.empty[Seq[String]], Option.empty[Calendar],
      """From: expeditor@example.com
        |To: recipient@example.com
        |Subject: test subject
        |
        |Test content
        |abcdefghijklmnopqrstuvwxyz
        |0123456789""".stripMargin).check(ok)))
  private val setAllMessagesInMailboxASeenFlag = imap("storeAll").store(MessageRanges(From(1L)), StoreFlags.add(Silent.Enable(), "\\Seen")).check(ok)
  private def copyAllMessagesToMailbox(mailbox: String) = imap("copy").copyMessage(MessageRanges(From(1L)), mailbox).check(ok)
  private def moveAllMessagesToMailbox(mailbox: String) = imap("move").moveMessage(MessageRanges(From(1L)), mailbox).check(ok)
  private val setAllMessagesInMailboxBDeletedFlagAndExpunge = exec(imap("storeAll").store(MessageRanges(From(1L)), StoreFlags.add(Silent.Enable(), "\\Deleted")).check(ok))
    .exec(imap("expunge").expunge().check(ok))
  private val deleteMailboxA_B_C_X_Y_Z_T = exec(imap("deleteFolder").deleteFolder(mailboxAName).check(ok))
    .exec(imap("deleteFolder").deleteFolder(mailboxBName).check(ok))
    .exec(imap("deleteFolder").deleteFolder(mailboxCName).check(ok))
    .exec(imap("deleteFolder").deleteFolder(mailboxXName).check(ok))
    .exec(imap("deleteFolder").deleteFolder(mailboxYName).check(ok))
    .exec(imap("deleteFolder").deleteFolder(mailboxZName).check(ok))
    .exec(imap("deleteFolder").deleteFolder(mailboxTName).check(ok))
  private val createMailboxDWith1000SubMailboxes = exec(imap("createFolder").createFolder(mailboxDName).check(ok))
    .exec(repeat(numberOfSubMailboxes, "loopId")(pause(gracePeriod).exec(exec(imap("createFolder").createFolder(s"$mailboxDName.$${loopId}").check(ok)))))
  private val renameMailboxD = exec(imap("renameFolder").renameFolder(mailboxDName, mailboxDNewName).check(ok))
  private val deleteMailboxD = exec(imap("deleteFolder").deleteFolder(mailboxDNewName).check(ok))
    .exec(repeat(numberOfSubMailboxes, "loopId")(pause(gracePeriod).exec(exec(imap("deleteFolder").deleteFolder(s"$mailboxDNewName.$${loopId}").check(ok)))))


  def generate(feeder: FeederBuilder): ScenarioBuilder =
    scenario("MassiveOperationSupport")
      .feed(feeder)
      .pause(1 second)
      .exec(imap("Connect").connect()).exitHereIfFailed
      .exec(imap("login").login("${username}", "${password}").check(ok))
      .exec(createMailboxA_B_C_X_Y_Z_T)
      .exec(imap("select").select(mailboxAName).check(ok))
      .exec(populateMailboxA)
      .pause(2 second)
      .exec(setAllMessagesInMailboxASeenFlag)
      .pause(2 second)
      .exec(exec(copyAllMessagesToMailbox(mailboxBName))).pause(2 seconds)
      .exec(exec(copyAllMessagesToMailbox(mailboxZName))).pause(2 seconds)
      .exec(exec(copyAllMessagesToMailbox(mailboxTName))).pause(2 seconds)
      .exec(moveAllMessagesToMailbox(mailboxCName)).pause(2 seconds)
      .exec(imap("select").select(mailboxBName).check(ok))
      .exec(setAllMessagesInMailboxBDeletedFlagAndExpunge).pause(2 seconds)
      .exec(imap("select").select(mailboxCName).check(ok)).exec(moveAllMessagesToMailbox(mailboxXName))
      .pause(2 seconds)
      .exec(imap("select").select(mailboxXName).check(ok)).exec(moveAllMessagesToMailbox(mailboxYName))
      .pause(2 seconds)
      .exec(deleteMailboxA_B_C_X_Y_Z_T)
      .exec(createMailboxDWith1000SubMailboxes)
      .pause(1 seconds)
      .exec(renameMailboxD)
      .pause(5 seconds)
      .exec(deleteMailboxD)

}
