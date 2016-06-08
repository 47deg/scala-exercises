package com.fortysevendeg.exercises.services.free

import cats.free.Free
import cats.free.Inject
import com.fortysevendeg.github4s.free.domain.{ Commit, User, OAuthToken, Authorize }

/**
 * GitHub Ops GADT
 */
sealed trait GithubOp[A]

final case class GetAuthorizeUrl(
  clientId: String,
  redirectUri: String,
  scopes: List[String]
) extends GithubOp[Authorize]

final case class GetAccessToken(
  clientId: String,
  clientSecret: String,
  code: String,
  redirectUri: String,
  state: String
) extends GithubOp[OAuthToken]

final case class GetAuthUser(accessToken: Option[String] = None) extends GithubOp[User]

final case class GetContributions(owner: String, repo: String, path: String) extends GithubOp[List[Commit]]

/**
 * Exposes GitHub operations as a Free monadic algebra that may be combined with other Algebras via
 * Coproduct
 */
class GithubOps[F[_]](implicit I: Inject[GithubOp, F]) {

  def getAuthorizeUrl(
    clientId: String,
    redirectUri: String,
    scopes: List[String] = List.empty
  ): Free[F, Authorize] =
    Free.inject[GithubOp, F](GetAuthorizeUrl(clientId, redirectUri, scopes))

  def getAccessToken(
    clientId: String,
    clienteSecret: String,
    code: String,
    redirectUri: String,
    state: String
  ): Free[F, OAuthToken] =
    Free.inject[GithubOp, F](GetAccessToken(clientId, clienteSecret, code, redirectUri, state))

  def getAuthUser(accessToken: Option[String] = None): Free[F, User] = Free.inject[GithubOp, F](GetAuthUser(accessToken))

  def getContributions(owner: String, repo: String, path: String): Free[F, List[Commit]] =
    Free.inject[GithubOp, F](GetContributions(owner, repo, path))

}

/**
 * Default implicit based DI factory from which instances of the GuthubOps may be obtained
 */
object GithubOps {
  implicit def instance[F[_]](implicit I: Inject[GithubOp, F]): GithubOps[F] = new GithubOps[F]
}