FROM openjdk:11

# Uncomment below and comment above line (i.e., FROM openjdk:8) for OS-specific (e.g., Alpine OS) docker base image
# FROM openjdk:8-jdk-alpine

# Build-time arguments for configuration
ARG spring_config_label
ARG active_profile
ARG spring_config_url
ARG management_rmi_server_hostname
ARG management_jmxremote_rmi_port

# Environment variables passed at runtime
ENV active_profile_env=${active_profile}
ENV spring_config_label_env=${spring_config_label}
ENV iam_adapter_url_env=${iam_adapter_url}

# User configuration
ARG container_user=mosip
ARG container_user_group=mosip
ARG container_user_uid=1001
ARG container_user_gid=1001

# New ARGs and Labels for source control and build metadata
ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME

LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

# Install required packages and create the user
RUN apt-get -y update \
    && apt-get install -y unzip sudo \
    && groupadd -g ${container_user_gid} ${container_user_group} \
    && useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/sh -m ${container_user} \
    && adduser ${container_user} sudo

# Set working directory
WORKDIR /home/${container_user}
ENV work_dir=/home/${container_user}

# Create a directory for additional jars
ARG loader_path=${work_dir}/additional_jars/
RUN mkdir -p ${loader_path}
ENV loader_path_env=${loader_path}

# Volume configuration
VOLUME ${work_dir}/logs ${work_dir}/Glowroot

# Add the application JAR
ADD ./target/registration-processor-registration-status-service-*.jar registration-processor-registration-status-service.jar

# Expose required ports
EXPOSE 8083
EXPOSE 4000

# Set permissions
RUN chown -R ${container_user}:${container_user} /home/${container_user}

# Set user
USER ${container_user_uid}:${container_user_gid}

# Entry point
CMD if [ "$active_profile_env" = "preprod" ]; then \
        wget 'http://13.71.87.138:8040/artifactory/libs-release-local/io/mosip/testing/glowroot.zip' ; \
        wget "${iam_adapter_url_env}" -O "${loader_path_env}"/kernel-auth-adapter.jar; \
        unzip glowroot.zip ; \
        rm -rf glowroot.zip ; \
        sed -i 's/<service_name>/registration-processor-registration-status-service/g' glowroot/glowroot.properties ; \
        java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -XX:+HeapDumpOnOutOfMemoryError -XX:+UseG1GC -XX:+UseStringDeduplication -jar -javaagent:glowroot/glowroot.jar -Dloader.path="${loader_path_env}" -Dspring.cloud.config.label="${spring_config_label_env}" -Dspring.profiles.active="${active_profile_env}" -Dspring.cloud.config.uri="${spring_config_url_env}" registration-processor-registration-status-service.jar ; \
    else \
        wget "${iam_adapter_url_env}" -O "${loader_path_env}"/kernel-auth-adapter.jar; \
        java -Dloader.path="${loader_path_env}" -jar -Dspring.cloud.config.label="${spring_config_label_env}" -Dspring.profiles.active="${active_profile_env}" -Dspring.cloud.config.uri="${spring_config_url_env}" registration-processor-registration-status-service.jar; \
    fi
