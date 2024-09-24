# Base image
FROM openjdk:11

# Uncomment below and comment the above line for Alpine-based image
# FROM openjdk:8-jdk-alpine

# Define build arguments
ARG spring_config_label
ARG active_profile
ARG spring_config_url
ARG management_rmi_server_hostname
ARG management_jmxremote_rmi_port
ARG iam_adapter_url

# Define additional build arguments for source control information
ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME

# Set environment variables
ENV active_profile_env=${active_profile}
ENV spring_config_label_env=${spring_config_label}
ENV spring_config_url_env=${spring_config_url}
ENV iam_adapter_url_env=${iam_adapter_url}
ENV work_dir=/home/${container_user}
ENV loader_path=${work_dir}/additional_jars/

# Add labels for source control information
LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

# Build arguments for user configuration
ARG container_user=mosip
ARG container_user_group=mosip
ARG container_user_uid=1001
ARG container_user_gid=1001

# Install required packages and configure user
RUN apt-get -y update \
    && apt-get install -y unzip sudo \
    && groupadd -g ${container_user_gid} ${container_user_group} \
    && useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/sh -m ${container_user} \
    && adduser ${container_user} sudo

# Set working directory
WORKDIR /home/${container_user}

# Create directory for additional jars
RUN mkdir -p ${loader_path}
ENV loader_path_env=${loader_path}

# Define volumes
VOLUME /home/ftp1/ARCHIVE_PACKET_LOCATION /home/ftp1/LANDING_ZONE ${work_dir}/logs ${work_dir}/Glowroot

# Copy the jar file to the container
ADD ./target/registration-processor-landing-zone-*.jar registration-processor-landing-zone.jar

# Change permissions of files and directories
RUN chown -R ${container_user}:${container_user} /home/${container_user}

# Switch to the created user
USER ${container_user_uid}:${container_user_gid}

# Command to run the application, with conditional handling for preprod profile
CMD if [ "$active_profile_env" = "preprod" ]; then \
        wget 'http://13.71.87.138:8040/artifactory/libs-release-local/io/mosip/testing/glowroot.zip' ; \
        wget "${iam_adapter_url_env}" -O "${loader_path_env}/kernel-auth-adapter.jar"; \
        unzip glowroot.zip ; \
        rm -rf glowroot.zip ; \
        sed -i 's/<service_name>/registration-processor-registration-status-service/g' glowroot/glowroot.properties ; \
        java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -XX:+HeapDumpOnOutOfMemoryError -XX:+UseG1GC -XX:+UseStringDeduplication \
             -javaagent:glowroot/glowroot.jar \
             -Dloader.path="${loader_path_env}" \
             -Dspring.cloud.config.label="${spring_config_label_env}" \
             -Dspring.profiles.active="${active_profile_env}" \
             -Dspring.cloud.config.uri="${spring_config_url_env}" \
             -jar registration-processor-landing-zone.jar; \
    else \
        wget "${iam_adapter_url_env}" -O "${loader_path_env}/kernel-auth-adapter.jar"; \
        java -Dloader.path="${loader_path_env}" \
             -Dspring.cloud.config.label="${spring_config_label_env}" \
             -Dspring.profiles.active="${active_profile_env}" \
             -Dspring.cloud.config.uri="${spring_config_url_env}" \
             -jar registration-processor-landing-zone.jar; \
    fi
