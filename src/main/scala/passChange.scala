package webapp.auth

import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import webapp.db.postgrest.Database, Database.{Predicate => Pred}

abstract class PasswordChange(val cc: ControllerComponents,
                              val authenticated: Authenticated)(
    implicit val ec: ExecutionContext
) extends AbstractController(cc) {

  val usersTable: Database.Endpoint

  def changePassword = authenticated.async { request =>
    val form = request.body.asFormUrlEncoded
    val absentParams = Future.successful {
      BadRequest("Form parameters cannot be absent")
    }

    form.fold(absentParams) { form =>
      form.get("current").fold(absentParams) {
        _.headOption.fold(absentParams) { currentPassword =>
          form.get("new").fold(absentParams) {
            _.headOption.fold(absentParams) { newPassword =>
              form.get("renew").fold(absentParams) {
                _.headOption.fold(absentParams) { reNewPassword =>
                  val id = request.user

                  // New password cannot be equal to old password
                  // New password cannot be empty
                  if (newPassword != currentPassword) {
                    if (newPassword == reNewPassword) {
                      if (!newPassword.isEmpty) {
                        usersTable.select.singular
                          .columns("password")
                          .where(
                            Pred.eq("id", id)
                          )
                          .onFailure { _ =>
                            InternalServerError(
                              "An error ocurred while updating the password"
                            )
                          }
                          .onSuccess { response =>
                            val user = response.json.as[JsObject]
                            val hashed =
                              user("password").as[String].stripPrefix("\\x")

                            val authorized =
                              Auth.password.verify(hashed, currentPassword)

                            if (authorized) {
                              usersTable
                                .update(
                                  "password" -> ("\\x" ++ Auth.password.hash(
                                    newPassword))
                                )
                                .where(
                                  Pred.eq("id", id)
                                )
                                .onFailure { _ =>
                                  InternalServerError(
                                    "An error ocurred while updating the password"
                                  )
                                }
                                .onSuccess { _ =>
                                  Ok("Password successfully changed!")
                                }
                            } else {
                              Future.successful {
                                BadRequest(
                                  "An error occurred. " ++
                                    "Check that your current password is not correct"
                                )
                              }
                            }
                          }
                      } else {
                        Future.successful {
                          BadRequest("The new password cannot be empty")
                        }
                      }
                    } else {
                      Future.successful {
                        BadRequest(
                          "You have to repeat your new password correctly!"
                        )
                      }
                    }
                  } else {
                    Future.successful {
                      BadRequest(
                        "Your new password cannot be equal to the old one"
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
