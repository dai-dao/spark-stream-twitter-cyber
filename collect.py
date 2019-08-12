import tweepy
import configparser
import pandas as pd


#
f = open("accounts.txt", 'r')
fl = f.readlines()
accounts = [a.strip() for a in fl]

#
config = configparser.ConfigParser()
config.read("twitter.cfg")

consumer_key = config.get("CONSUMER", "key")
consumer_secret = config.get("CONSUMER", "secret")
access_token = config.get("ACCESS", "token")
access_token_secret = config.get("ACCESS", "secret")

#
auth = tweepy.OAuthHandler(consumer_key, consumer_secret)
auth.set_access_token(access_token, access_token_secret)
api = tweepy.API(auth)

#
pd_tweets = []
pd_accounts = []
pd_retweets = []
pd_hash_tags = []
pd_user_mentions = []
for account in accounts:
    tweets = api.user_timeline(screen_name = account, count = 1000, tweet_mode = 'extended')
    for tweet in tweets:
        pd_accounts.append(account)
        hash_tags_json = tweet._json['entities']['hashtags']
        hash_tags = [ht['text'] for ht in hash_tags_json]
        pd_hash_tags.append(hash_tags)    
        user_mentions_json = tweet._json['entities']['user_mentions']
        user_mentions = [um['screen_name'] for um in user_mentions_json]
        pd_user_mentions.append(user_mentions)
        try:
            text = tweet._json['retweeted_status']['full_text']
            pd_retweets.append(True)
        except:
            text = tweet._json['full_text']
            pd_retweets.append(False)
        pd_tweets.append(text)
    
#
df = pd.DataFrame({
    "account" : pd_accounts,
    "retweet" : pd_retweets,
    "hash_tags" : pd_hash_tags,
    "user_mentions" : pd_user_mentions, 
    "tweet" : pd_tweets
})
df.to_csv("tweets_data.csv", index=False)
