import com.github.scribejava.core.builder.api.DefaultApi10a
import com.github.scribejava.core.model.OAuth1RequestToken

public class TelldusApi extends DefaultApi10a {

  private static final String AUTHORIZE_URL = "https://api.telldus.com/oauth/authorize?oauth_token=%s"
  private static final String REQUEST_TOKEN_ENDPOINT = "https://api.telldus.com/oauth/requestToken"
  private static final String ACCESS_TOKEN_ENDPOINT = "https://api.telldus.com/oauth/accessToken"

  protected TelldusApi() {
  }

  private static class InstanceHolder {
      private static final TelldusApi INSTANCE = new TelldusApi()
  }

  public static TelldusApi instance() {
      return InstanceHolder.INSTANCE
  }

  @Override
  public String getAccessTokenEndpoint() {
      return ACCESS_TOKEN_ENDPOINT
  }

  @Override
  public String getRequestTokenEndpoint() {
      return REQUEST_TOKEN_ENDPOINT
  }

  @Override
  public String getAuthorizationUrl(OAuth1RequestToken requestToken) {
      return String.format(AUTHORIZE_URL, requestToken.getToken())
  }

}
