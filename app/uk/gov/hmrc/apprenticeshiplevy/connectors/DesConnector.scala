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

package uk.gov.hmrc.apprenticeshiplevy.connectors

import org.joda.time.LocalDate
import play.api.Logger
import uk.gov.hmrc.apprenticeshiplevy.config.{AppContext, WSHttp}
import uk.gov.hmrc.apprenticeshiplevy.data.des._
import uk.gov.hmrc.apprenticeshiplevy.data.api._
import uk.gov.hmrc.apprenticeshiplevy.utils.{DateRange, ClosedDateRange}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, NotFoundException}
import views.html.helper

import scala.concurrent.{Future, ExecutionContext}

trait DesUrl {
  def baseUrl: String
}

trait DesSandboxUrl extends DesUrl {
  def baseUrl: String = AppContext.desUrl
}

trait DesProductionUrl extends DesUrl {
  def baseUrl: String = AppContext.stubDesUrl
}

trait EmployerDetailsEndpoint {
  des: DesConnector =>

  def designatoryDetails(empref: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[DesignatoryDetails] = {
    val url = s"${des.baseUrl}/epaye/${helper.urlEncode(empref)}/designatory-details"

    // $COVERAGE-OFF$
    Logger.debug(s"Calling DES at $url")
    // $COVERAGE-ON$

    des.httpGet
      .GET[DesignatoryDetails](url)
      .map(_.copy(empref = Some(empref)))(ec)
  }
}

trait EmploymentCheckEndpoint {
  des: DesConnector =>

  def check(empref: String, nino: String, dateRange: ClosedDateRange)
           (implicit hc: HeaderCarrier, ec: scala.concurrent.ExecutionContext): Future[EmploymentCheckStatus] = {
    val url = s"$baseUrl/apprenticeship-levy/employers/${helper.urlEncode(empref)}/employed/${helper.urlEncode(nino)}?${dateRange.paramString}"

    // $COVERAGE-OFF$
    Logger.debug(s"Calling DES at $url")
    // $COVERAGE-ON$

    des.httpGet.GET[EmploymentCheckStatus](url) recover { case notFound: NotFoundException => NinoUnknown }
  }
}

trait FractionsEndpoint {
  des: DesConnector =>

  def fractions(empref: String, dateRange: DateRange)(implicit hc: HeaderCarrier): Future[Fractions] = {
    val url = (s"$baseUrl/apprenticeship-levy/employers/${helper.urlEncode(empref)}/fractions", dateRange.toParams) match {
      case (u, Some(params)) => s"$u?$params"
      case (u, None) => u
    }

    // $COVERAGE-OFF$
    Logger.debug(s"Calling DES at $url")
    // $COVERAGE-ON$

    des.httpGet.GET[Fractions](url)
  }

  def fractionCalculationDate(implicit hc: HeaderCarrier, ec: scala.concurrent.ExecutionContext): Future[LocalDate] = {
    val url = s"$baseUrl/apprenticeship-levy/fraction-calculation-date"

    // $COVERAGE-OFF$
    Logger.debug(s"Calling DES at $url")
    // $COVERAGE-ON$

    des.httpGet.GET[FractionCalculationDate](url) map {
      _.date
    }
  }
}

trait LevyDeclarationsEndpoint {
  des: DesConnector =>

  def eps(empref: String, dateRange: DateRange)(implicit hc: HeaderCarrier): Future[EmployerPaymentsSummary] = {
    val url = (s"$baseUrl/rti/employers/${helper.urlEncode(empref)}/employer-payment-summary", dateRange.toParams) match {
      case (u, None) => u
      case (u, Some(ps)) => s"$u?$ps"
    }

    // $COVERAGE-OFF$
    Logger.debug(s"Calling DES at $url")
    // $COVERAGE-ON$

    des.httpGet.GET[EmployerPaymentsSummary](url)
  }
}

trait DesConnector extends DesUrl
                   with FractionsEndpoint
                   with EmployerDetailsEndpoint
                   with EmploymentCheckEndpoint
                   with LevyDeclarationsEndpoint {
  def httpGet: HttpGet
}

object LiveDesConnector extends DesConnector with DesProductionUrl {
  def httpGet: HttpGet = WSHttp
}

object SandboxDesConnector extends DesConnector with DesSandboxUrl {
  def httpGet: HttpGet = WSHttp
}
