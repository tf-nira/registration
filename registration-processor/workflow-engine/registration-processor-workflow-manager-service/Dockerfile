FROM openjdk:11

# Uncomment below and Comment above line(i.e. FROM openjdk:8) for OS specific (e.g. Alpine OS ) docker base image
# FROM openjdk:8-jdk-alpine

# Build-time arguments
ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME
ARG spring_config_label
ARG active_profile
ARG spring_config_url
ARG is_glowroot
ARG artifactory_url
ARG container_user=mosip
ARG container_user_group=mosip
ARG container_user_uid=1001
ARG container_user_gid=1001

# Labels for build metadata
LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

# Environment variables for runtime
ENV active_profile_env=${active_profile}
ENV spring_config_label_env=${spring_config_label}
ENV is_glowroot_env=${is_glowroot}
ENV artifactory_url_env=${artifactory_url}
ENV iam_adapter_url_env=${iam_adapter_url}
ENV work_dir=/home/${container_user}
ENV loader_path_env=${work_dir}/additional_jars/

# Create user and install dependencies
RUN apt-get -y update \
    && apt-get install -y unzip sudo \
    && groupadd -g ${container_user_gid} ${container_user_group} \
    && useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/sh -m ${container_user} \
    && adduser ${container_user} sudo

# Set working directory
WORKDIR ${work_dir}

# Create loader path
RUN mkdir -p ${loader_path_env}

# Define volume
VOLUME ${work_dir}/logs ${work_dir}/Glowroot

# Copy application JAR
ADD ./target/registration-processor-workflow-manager-service-*.jar registration-processor-workflow-manager-service.jar

# Change ownership of working directory
RUN chown -R ${container_user}:${container_user} ${work_dir}

# Switch to container user
USER ${container_user_uid}:${container_user_gid}

# Default command to run the application
CMD wget "${artifactory_url_env}"/artifactory/libs-release-local/io/mosip/testing/regproc-reprocessor-ceylon-cache-repo.zip ; \
    wget "${iam_adapter_url_env}" -O "${loader_path_env}"/kernel-auth-adapter.jar; \
    unzip regproc-reprocessor-ceylon-cache-repo.zip ; \
    rm -rf regproc-reprocessor-ceylon-cache-repo.zip ; \
    if [ "$is_glowroot_env" = "present" ]; then \
        wget "${artifactory_url_env}"/artifactory/libs-release-local/io/mosip/testing/glowroot.zip ; \
        unzip glowroot.zip ; \
        rm -rf glowroot.zip ; \
        sed -i 's/<service_name>/registration-processor-workflow-manager-service/g' glowroot/glowroot.properties ; \
        java -jar -javaagent:glowroot/glowroot.jar -Dloader.path="${loader_path_env}" -Dspring.cloud.config.label="${spring_config_label_env}" -Dspring.profiles.active="${active_profile_env}" -Dspring.cloud.config.uri="${spring_config_url}" -Dceylon.cache.repo=./regproc-reprocessor-ceylon-cache-repo registration-processor-workflow-manager-service.jar ; \
    else \
        java -Dloader.path="${loader_path_env}" -jar -Dspring.cloud.config.label="${spring_config_label_env}" -Dspring.profiles.active="${active_profile_env}" -Dspring.cloud.config.uri="${spring_config_url}" -Dceylon.cache.repo=./regproc-reprocessor-ceylon-cache-repo registration-processor-workflow-manager-service.jar ; \
    fi
