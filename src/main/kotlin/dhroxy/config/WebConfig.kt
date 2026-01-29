package dhroxy.config

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

@Configuration
class WebConfig : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        // Serve static files from classpath:/static/
        // For any path that doesn't match a file, return index.html (for React Router)
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(object : PathResourceResolver() {
                override fun getResource(resourcePath: String, location: Resource): Resource? {
                    val requestedResource = location.createRelative(resourcePath)

                    // If the resource exists and is readable, return it
                    if (requestedResource.exists() && requestedResource.isReadable) {
                        return requestedResource
                    }

                    // For non-API paths, return index.html for React Router
                    if (!resourcePath.startsWith("fhir/") && !resourcePath.startsWith("api/")) {
                        return ClassPathResource("/static/index.html")
                    }

                    return null
                }
            })
    }
}
