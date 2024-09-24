# Use the official Nginx image as the base
FROM nginx

# Define build-time arguments
ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME

# Apply labels with build information
LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

# Define volume
VOLUME /home/mosip

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Copy healthcheck file
COPY healthcheck.txt /home/mosip/landing/healthcheck.txt

# Expose port 8082
EXPOSE 8082

# Command to start Nginx in the foreground
CMD ["nginx", "-g", "daemon off;"]

# Optional: Add a health check (customize as necessary)
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl --fail http://localhost:8082/healthcheck.txt || exit 1
