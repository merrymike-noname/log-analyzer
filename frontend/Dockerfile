FROM nginx:1.27-alpine

# Replace default config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy static files
COPY . /usr/share/nginx/html/

# Remove Dockerfile-related files from the served directory
RUN rm -f /usr/share/nginx/html/Dockerfile /usr/share/nginx/html/.dockerignore /usr/share/nginx/html/nginx.conf

EXPOSE 80