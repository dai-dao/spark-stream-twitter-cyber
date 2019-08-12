gcloud dataproc clusters delete demo-cluster --quiet
gcloud functions delete http_function --quiet
gcloud pubsub topics delete tweets --quiet
gcloud pubsub subscriptions delete tweets-subscription --quiet
gcloud iam service-accounts delete $SERVICE_ACCOUNT_NAME@$PROJECT.iam.gserviceaccount.com --quiet


export ACCESS_TOKEN=$(gcloud auth print-access-token)

curl -X POST \
    -H "Authorization: Bearer "$ACCESS_TOKEN \
    -H "Content-Type: application/json; charset=utf-8" \
    --data "{
      'gqlQuery': {
        'allowLiterals': true,
        'queryString': '
          SELECT * from TrendingHashtags
          ORDER BY datetime DESC
          LIMIT 5'
      }
    }" \
    "https://datastore.googleapis.com/v1beta3/projects/$PROJECT:runQuery"