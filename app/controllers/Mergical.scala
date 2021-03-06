package controllers

import Authentication.Authenticated
import play.api.mvc.{Controller, Action, RequestHeader}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.future
import models.{User, Source, Generator, VCalendar}
import Serializers._

object Mergical extends Controller {

  /**
   * Landing page
   */
  val index = Action {
    Ok(views.html.index())
  }

  /**
   * User dashboard: show iCal feeds and feed generator
   */
  val dashboard = Authenticated { implicit request =>
    Ok(views.html.dashboard(request.username, Json.toJson(User.byId(request.userId))))
  }

  /**
   * Sign user out
   */
  val signOut = Action { implicit request =>
    Authentication.signOut(Redirect(routes.Mergical.index()))
  }

  /**
   * Add a feed to a user
   * @param name Name for the feed
   * @param url Feed URL to add
   * @return The source id if the operation was successful, otherwise 500
   */
  def addFeed(name: String, url: String) = Authenticated { implicit request =>
      Source.add(request.userId, name, url) match {
        case Some(id) => Ok(Json.toJson(id))
        case None => InternalServerError
      }
  }

  /**
   * Remove a feed to a user
   * @param id Id of the feed to remove
   * @return 200 if the operation was successful, otherwise 500
   */
  def removeFeed(id: String) = Authenticated { implicit request =>
    if (Source.remove(request.userId, id)) Ok else InternalServerError
  }

  /**
   * @param id Feed id
   * @return The generated feed
   */
  def generator(id: String) = Action { implicit request =>
    Async {
      Generator.getSources(id) match {
        case Some((feed, name)) => VCalendar(feed, name).map(Ok(_).as("text/calendar;charset="+implicitly[play.api.mvc.Codec].charset))
        case None => future(NotFound)
      }
    }
  }

  val generatorForm = Form[(String, Seq[(String, Boolean)])](tuple(
    "name" -> nonEmptyText,
    "entries" -> seq(tuple(
      "feed" -> nonEmptyText,
      "private" -> boolean
    ))
  ))

  val addGeneratorForm = Authenticated { implicit request =>
    Ok(views.html.addGeneratorForm(request.username, Json.toJson(User.byId(request.userId))))
  }

  /**
   * Add a generator for the current user
   * @return The generator id if the operation was successful, otherwise 500
   */
  val addGenerator = Authenticated { implicit request =>
    generatorForm.bindFromRequest.fold(
      { _ => BadRequest },
      { case (name, entries) =>
        Generator.add(request.userId, name, entries) match {
          case Some(id) => Ok(Json.toJson(id))
          case None => InternalServerError
        }
      }
    )
  }

  /**
   * Remove a generator of the current user (if he owns it)
   * @param id Generator id
   * @return 200 if successful, otherwise 500
   */
  def removeGenerator(id: String) = Authenticated { implicit request =>
    if (Generator.remove(request.userId, id)) Ok else InternalServerError
  }
}