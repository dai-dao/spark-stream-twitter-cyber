# Start with a BLANK project
export PROJECT=$(gcloud info --format='value(config.project)')
export SERVICE_ACCOUNT_NAME="dataproc-service-account-2"

# Enable APIs
gcloud services enable \
    dataproc.googleapis.com \
    pubsub.googleapis.com \
    cloudfunctions.googleapis.com \
    datastore.googleapis.com

# Active Cloud DataStore by creating an App Engine app
gcloud app create --region=us-central

# Creates the tweet topic
gcloud pubsub topics create tweets

# Creates subscription
gcloud pubsub subscriptions create tweets-subscription --topic=tweets

# Create service accounts for Dataproc
gcloud iam service-accounts create $SERVICE_ACCOUNT_NAME

# Add Dataproc Worker IAM role
gcloud projects add-iam-policy-binding $PROJECT \
    --role roles/dataproc.worker \
    --member="serviceAccount:$SERVICE_ACCOUNT_NAME@$PROJECT.iam.gserviceaccount.com"

# Add Datastore user IAM role
gcloud projects add-iam-policy-binding $PROJECT \
    --role roles/datastore.user \
    --member="serviceAccount:$SERVICE_ACCOUNT_NAME@$PROJECT.iam.gserviceaccount.com"

# Add Cloud Pub/Sub subscriber IAM role
gcloud beta pubsub subscriptions add-iam-policy-binding \
    tweets-subscription \
    --role roles/pubsub.subscriber \
    --member="serviceAccount:$SERVICE_ACCOUNT_NAME@$PROJECT.iam.gserviceaccount.com"

# Create Dataproc cluster
gcloud dataproc clusters create demo-cluster \
    --zone=us-central1-a \
    --scopes=pubsub,datastore \
    --image-version=1.2 \
    --worker-machine-type=n1-standard-2 \
    --master-machine-type=n1-standard-2 \
    --service-account="$SERVICE_ACCOUNT_NAME@$PROJECT.iam.gserviceaccount.com"
