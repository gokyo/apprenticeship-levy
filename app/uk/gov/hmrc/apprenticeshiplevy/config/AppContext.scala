/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.apprenticeshiplevy.config

import play.api.Play
import play.api.{Configuration, Logger, Mode, Application}
import uk.gov.hmrc.play.config.ServicesConfig
import scala.util.{Try, Success, Failure}
import uk.gov.hmrc.play.config.RunMode
import uk.gov.hmrc.apprenticeshiplevy.connectors.ServiceLocatorConnector
import uk.gov.hmrc.play.http.HeaderCarrier

trait ServiceLocatorRegistration extends RunMode {
  def registrationEnabled: Boolean
  val slConnector: ServiceLocatorConnector

  // replacing GlobalSettings.onStart
  registrationEnabled match {
    case true => {
      AppContext.maybeApp.map { app =>
        slConnector.register(HeaderCarrier())
      }.orElse{
        Logger.error("Registration in Service Locator is disabled due to no app started")
        None
      }
    }
    case false => Logger.warn("Registration in Service Locator is disabled")
  }
}

object AppContext extends ServicesConfig with ServiceLocatorRegistration {
  Logger.info(s"""\n${"*" * 80}\n""")

  override lazy val slConnector = ServiceLocatorConnector

  def maybeApp: Option[Application] = Try(Play.maybeApplication).getOrElse(None)

  def maybeConfiguration: Option[Configuration] = maybeApp.map(_.configuration)

  def appName: String = maybeString("appName").getOrElse{
    Logger.error("appName is not configured")
    ""
  }

  def appUrl: String = maybeString("appUrl").getOrElse{
    Logger.error("appUrl is not configured")
    ""
  }

  def serviceLocatorUrl: String = maybeBaseURL("service-locator").getOrElse("")

  override def registrationEnabled: Boolean =
    maybeString("microservice.services.service-locator.enabled")
      .flatMap(flag => Try(flag.toBoolean).toOption)
      .getOrElse {
        // $COVERAGE-OFF$
        Logger.warn("A configuration value has not been provided for service-locator.enabled, defaulting to true")
        // $COVERAGE-ON$
        true
      }

  def privateModeEnabled: Boolean = maybeString("microservice.private-mode")
    .flatMap(flag => Try(flag.toBoolean).toOption)
    .getOrElse {
      // $COVERAGE-OFF$
      Logger.warn("A configuration value has not been provided for microservice.private-mode, defaulting to true")
      // $COVERAGE-ON$
      true
    }

  def whitelistedApplicationIds: Seq[String] = maybeString("microservice.whitelisted-applications")
    .map { applicationIds => applicationIds.split(",").toSeq }.getOrElse(Seq.empty)

  // $COVERAGE-OFF$
  Logger.info(s"""\n${"*" * 80}\nWhite list:\n${whitelistedApplicationIds.mkString(", ")}\n${"*" * 80}\n""")
  // $COVERAGE-ON$

  def desEnvironment: String = maybeString("microservice.services.des.env").getOrElse("")

  def desToken: String = maybeString("microservice.services.des.token").getOrElse("")

  def metricsEnabled: Boolean = maybeString("microservice.metrics.graphite.enabled").flatMap(flag => Try(flag.toBoolean).toOption).getOrElse(false)

  def getURL(name: String) = Try {
      val url = maybeBaseURL(name).getOrElse("")
      val host = maybeString(s"microservice.services.${name}.host").getOrElse("")
      val path = maybeString(s"microservice.services.${name}.path").getOrElse("")
      val port = maybeString(s"microservice.services.${name}.port").getOrElse("")
      val protocol = maybeString(s"microservice.services.${name}.protocol").getOrElse("")
      if (port.isEmpty) {
        s"${protocol}://${host}"
      } else {
        val baseurl = if (maybeApp.map(_.mode).getOrElse(Mode.Test) == Mode.Prod && !url.contains("localhost")) appUrl else url
        if (path == "") url else s"${baseurl}${path}"
      }
    }.getOrElse(maybeBaseURL(name).getOrElse(""))

  def authUrl: String = getURL("auth")

  def desUrl: String = getURL("des")

  def stubURL(name: String) = Try {
      val stubUrl = maybeBaseURL(s"stub-${name}").getOrElse("")
      val path = maybeString(s"microservice.services.stub-${name}.path").getOrElse("")
      val baseurl = if (maybeApp.map(_.mode).getOrElse(Mode.Test) == Mode.Prod && !stubUrl.contains("localhost")) appUrl else stubUrl
      s"${baseurl}${path}"
    }.getOrElse(maybeBaseURL(s"stub-${name}").getOrElse(""))

  def stubDesUrl: String = stubURL("des")

  def stubAuthUrl: String = stubURL("auth")

  // $COVERAGE-OFF$
  Logger.info(s"""\nStub: DES URL: ${stubDesUrl}    Stub Auth URL: ${stubAuthUrl}""")
  Logger.info(s"""\nDES URL: ${desUrl}    AUTH URL: ${authUrl}""")
  // $COVERAGE-ON$

  def datePattern(): String = maybeString("microservice.dateRegex").getOrElse("")

  def employerReferencePattern(): String = maybeString("microservice.emprefRegex").getOrElse("")

  def ninoPattern(): String = maybeString("microservice.ninoRegex").getOrElse("")

  // $COVERAGE-OFF$
  Logger.info(s"""\nWhite list:\n${whitelistedApplicationIds.mkString(", ")}\n""")
  // $COVERAGE-ON$

  // scalastyle:off
  def defaultNumberOfDeclarationYears: Int = maybeString("microservice.defaultNumberOfDeclarationYears").map(_.toInt).getOrElse(6)
  // scalastyle:on

  private def maybeString(id: String): Option[String] = maybeConfiguration.flatMap(_.getString(id))

  private def maybeBaseURL(name: String): Option[String] = Try(baseUrl(name)) match {
        case Success(v) => Some(v)
        case Failure(e) => {
          Logger.error(s"Unable to get baseUrl for ${name}. Error: ${e.getMessage()}")
          None
        }
      }

  Logger.info(s"""\n${"*" * 80}\n""")
}
