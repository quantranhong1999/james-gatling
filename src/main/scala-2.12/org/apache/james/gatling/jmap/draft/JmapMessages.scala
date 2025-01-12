package org.apache.james.gatling.jmap.draft

import io.gatling.core.Predef._
import io.gatling.core.session.Session
import io.gatling.http.Predef._
import io.gatling.http.check.HttpCheck
import org.apache.james.gatling.control.RecipientFeeder.RecipientFeederBuilder
import org.apache.james.gatling.control.{RecipientFeeder, User, UserFeeder}
import org.apache.james.gatling.jmap.draft.RetryAuthentication._
import org.apache.james.gatling.utils.RandomStringGenerator
import play.api.libs.json.{JsArray, JsString, Json => PlayJson}

case class MessageId(id: String = RandomStringGenerator.randomString) extends AnyVal
case class RecipientAddress(address: String) extends AnyVal
case class Subject(subject: String = RandomStringGenerator.randomString) extends AnyVal
case class TextBody(text: String = RandomStringGenerator.randomString) extends AnyVal

case class RequestTitle(title: String) extends AnyVal
case class Property(name: String) extends AnyVal

object RecipientAddress {
  def apply(user: User): RecipientAddress =
    RecipientAddress(user.username.value)
}

object JmapMessages {

  private val messageIdsPath = "$[0][1].messageIds[*]"

  type JmapParameters = String
  val NO_PARAMETERS : JmapParameters = ""

  val messageIdSessionParam = "messageId"
  val subjectSessionParam = "subject"
  val textBodySessionParam = "textBody"

  def sendMessages() =
    JmapAuthentication.authenticatedQuery("sendMessages", "/jmap")
      .body(StringBody(
        s"""[[
          "setMessages",
          {
            "create": {
              "$${$messageIdSessionParam}" : {
                "from": {"name":"$${${UserFeeder.usernameSessionParam}}", "email": "$${${UserFeeder.usernameSessionParam}}"},
                "to":  [{"name":"$${${RecipientFeeder.recipientSessionParam}}", "email": "$${${RecipientFeeder.recipientSessionParam}}"}],
                "textBody": "$${$textBodySessionParam}",
                "subject": "$${$subjectSessionParam}",
                "mailboxIds": ["$${${JmapMailbox.outboxMailboxIdSessionParam}}"]
              }
            }
          },
          "#0"
          ]]"""))

  def retrieveSentMessageIds() = {
    JmapAuthentication.authenticatedQuery("retrieveMessageIds", "/jmap")
      .body(StringBody(
        """[[
          "getMessageList",
          {
            "filter": {
              "inMailboxes" : [ "${sentMailboxId}" ]
            }
          },
          "#0"
          ]]"""))
      .check(saveMessageIds: _*)
  }

  def saveMessageIds: Seq[HttpCheck] = List(
    status.is(200),
    JmapChecks.noError,
    jsonPath(messageIdsPath).count.gt(0),
    jsonPath(messageIdsPath).findAll.saveAs("messageIds"))

  def moveMessagesToMailboxId =
    exec((session: Session) => session.set("update", {
        session("messageIds").as[Vector[String]].map(x =>s"""
            "$x" : { "mailboxIds": [ "${session("mailboxId").as[Vector[String]].head}" ] }"""
          )
          .mkString(",")
      }))
    .exec(JmapAuthentication.authenticatedQuery("moveSentMessagesToMessageId", "/jmap")
            .body(StringBody(
              """[[
                "setMessages",
                {
                  "update": { ${update} }
                },
                "#0"
                ]]"""))
            .check(hasBeenUpdated))

  def hasBeenUpdated =
    jsonPath("$..updated[*]").count.gt(0)

  def sendMessagesChecks(): Seq[HttpCheck] = List(
    status.is(200),
    JmapChecks.noError,
    JmapChecks.created())

  def sendMessagesWithRetryAuthentication() = {
    execWithRetryAuthentication(sendMessages(), sendMessagesChecks())
  }

  def sendMessagesToUserWithRetryAuthentication(recipientFeeder: RecipientFeederBuilder) = {
    val mailFeeder = Iterator.continually(
      Map(
        messageIdSessionParam -> MessageId().id,
        subjectSessionParam -> Subject().subject,
        textBodySessionParam -> TextBody().text
      )
    )
    feed(mailFeeder)
      .feed(recipientFeeder)
      .exec(sendMessagesWithRetryAuthentication())
  }

  def openpaasListMessageParameters(mailboxKey: String = "inboxID"): JmapParameters =
    s"""
       |  "filter": {
       |    "inMailboxes": ["$${$mailboxKey}"],
       |    "text": null
       |  },
       |  "sort": [ "date desc" ],
       |  "collapseThreads": false,
       |  "fetchMessages": false,
       |  "position": 0,
       |  "limit": 30
       |""".stripMargin

  def listMessages(queryParameters: JmapParameters = NO_PARAMETERS) =
    JmapAuthentication.authenticatedQuery("listMessages", "/jmap")
      .body(StringBody(
        s"""[[
          "getMessageList",
          {
            $queryParameters
          },
          "#0"
          ]]"""))

  val nonEmptyListMessagesChecks: Seq[HttpCheck] = List(
    status.is(200),
    JmapChecks.noError,
    jsonPath("$[0][1].messageIds[*]").findAll.saveAs("messageIds"))

  val listMessagesChecks: Seq[HttpCheck] = List(
      status.is(200),
      JmapChecks.noError,
      jsonPath("$[0][1].messageIds")
        .find
        .transform(rawMessageIds => PlayJson.parse(rawMessageIds)
          .as[JsArray]
          .value
          .map(_.as[JsString].value))
        .saveAs("messageIds"))

  def listMessagesWithRetryAuthentication() =
    execWithRetryAuthentication(listMessages(), nonEmptyListMessagesChecks)

  def getMessagesWithRetryAuthentication(properties: String, messageIdsKey: String = "messageIds") =
    execWithRetryAuthentication(getMessages(properties, messageIdsKey), getRandomMessageChecks)

  def getRandomMessagesWithRetryAuthentication() =
    execWithRetryAuthentication(getRandomMessages(), getRandomMessageChecks)

  val typicalMessageProperties: String = "[\"bcc\", \"cc\", \"date\", \"from\", \"hasAttachment\", \"htmlBody\", \"id\", \"isAnswered\", \"isDraft\", \"isFlagged\", \"isUnread\", \"mailboxIds\", \"size\", \"subject\", \"textBody\", \"to\"]"

  val previewMessageProperties: String = "[\"bcc\", \"blobId\", \"cc\", \"date\", \"from\", \"hasAttachment\", \"headers\", \"id\", \"isAnswered\", \"isDraft\", \"isFlagged\", \"isForwarded\", \"isUnread\", \"mailboxIds\", \"preview\", \"replyTo\", \"subject\", \"threadId\", \"to\"]"

  val openpaasInboxOpenMessageProperties: String = "[\"attachments\", \"bcc\", \"blobId\", \"cc\", \"date\", \"from\", \"hasAttachment\", \"headers\", \"htmlBody\", \"id\", \"isDraft\", \"isFlagged\", \"isUnread\", \"mailboxIds\", \"preview\", \"replyTo\", \"subject\", \"textBody\", \"threadId\", \"to\"]"





  def getRandomMessages(properties: String = typicalMessageProperties, messageIdsKey: String = "messageIds") =
    JmapAuthentication.authenticatedQuery("getMessages", "/jmap")
      .body(StringBody(
        s"""[[
          "getMessages",
          {
            "ids": ["$${$messageIdsKey.random()}"],
            "properties": $properties
          },
          "#0"
          ]]"""))


  def getMessages(properties: String, messageIdsKey: String = "messageIds") =
    JmapAuthentication.authenticatedQuery("getMessages", "/jmap")
      .body(StringBody(
        s"""[[
          "getMessages",
          {
            "ids": $${$messageIdsKey.jsonStringify()},
            "properties": $properties
          },
          "#0"
          ]]"""))

  val getRandomMessageChecks: Seq[HttpCheck] = List(
      status.is(200),
      JmapChecks.noError)

  def markAsRead() = performUpdateWithRetryAuthentication(RequestTitle("markAsRead"), Property("isUnread"), value = false)
  def markAsAnswered() = performUpdateWithRetryAuthentication(RequestTitle("markAsAnswered"), Property("isAnswered"), value = true)
  def markAsFlagged() = performUpdateWithRetryAuthentication(RequestTitle("markAsFlagged"), Property("isFlagged"), value = true)

  def performUpdate(title: RequestTitle, property: Property, value: Boolean) = {
    JmapAuthentication.authenticatedQuery(title.title, "/jmap")
      .body(StringBody(
        s"""[[
          "setMessages",
          {
            "update": {
              "$${messageIds.random()}" : {
                "${property.name}": "$value"
              }
            }
          },
          "#0"
          ]]"""))
  }

  val performUpdateChecks: Seq[HttpCheck] = List(
      status.is(200),
      JmapChecks.noError)

  def performUpdateWithRetryAuthentication(title: RequestTitle, property: Property, value: Boolean) = 
    execWithRetryAuthentication(performUpdate(title, property, value), performUpdateChecks)

}
