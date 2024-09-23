FROM openjdk:11

# Uncomment below and Comment above line (i.e., FROM openjdk:8) for OS specific (e.g., Alpine OS) docker base image
# FROM openjdk:8-jdk-alpine

# Build arguments passed during Docker build
ARG spring_config_label
ARG active_profile
ARG spring_config_url
ARG is_glowroot
ARG artifactory_url
ARG container_user=mosip
ARG container_user_group=mosip
ARG container_user_uid=1001
ARG container_user_gid=1001

# Added as per the requirement in your workflow
ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME

# Labels for metadata (used for build tracing)
LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

# Environment variables for runtime
ENV active_profile_env=${active_profile}
ENV spring_config_label_env=${spring_config_label}
ENV is_glowroot_env=${is_glowroot}
ENV artifactory_url_env=${artifactory_url}

# Apt-get and user setup
RUN apt-get -y update \
    && apt-get install -y unzip sudo \
    && groupadd -g ${container_user_gid} ${container_user_group} \
    && useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/sh -m ${container_user} \
    && adduser ${container_user} sudo

# Set working directory and loader path
WORKDIR /home/${container_user}
ENV work_dir=/home/${container_user}
ARG loader_path=${work_dir}/additional_jars/
RUN mkdir -p ${loader_path}
ENV loader_path_env=${loader_path}

# Change volume to storage directories
VOLUME ${work_dir}/logs ${work_dir}/Glowroot

# Add the application JAR file
ADD ./target/registration-processor-abis-*.jar registration-processor-abis.jar

# Change file permissions
RUN chown -R ${container_user}:${container_user} /home/${container_user}

# Select container user for all tasks
USER ${container_user_uid}:${container_user_gid}

# Conditional CMD for glowroot
CMD if [ "$is_glowroot_env" = "present" ]; then \
    wget "${artifactory_url_env}"/artifactory/libs-release-local/io/mosip/testing/glowroot.zip ; \
    unzip glowroot.zip ; \
    rm -rf glowroot.zip ; \
    sed -i 's/<service_name>/registration-processor-abis/g' glowroot/glowroot.properties ; \
    java -jar -javaagent:glowroot/glowroot.jar -Dspring.cloud.config.label="${spring_config_label_env}" -Dspring.profiles.active="${active_profile_env}" -Dspring.cloud.config.uri="${spring_config_url_env}" registration-processor-abis.jar ; \
    else \
    java -jar -Dspring.cloud.config.label="${spring_config_label_env}" -Dspring.profiles.active="${active_profile_env}" -Dspring.cloud.config.uri="${spring_config_url_env}" registration-processor-abis.jar ; \
    fi
