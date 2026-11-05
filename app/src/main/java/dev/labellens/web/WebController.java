package dev.labellens.web;

import dev.labellens.product.InvalidUploadException;
import dev.labellens.product.ProductNotFoundException;
import dev.labellens.product.ProductResponse;
import dev.labellens.product.ProductService;
import dev.labellens.product.UploadLimitException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * The web pages (day 10). Same ProductService as the JSON API: the pages show exactly what
 * GET /api/products/{id} returns, nothing is computed twice.
 *
 * The upload form works without JavaScript (normal POST, then a redirect to the result page).
 * With htmx, the same form posts in the background, shows "Reading the label…", and only the
 * result is swapped into the page; the address bar still gets the product's URL (HX-Push-Url).
 */
@Controller
public class WebController {

    static final int RECENT = 10;

    private final ProductService service;

    public WebController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("recent", service.list(0, RECENT).items());
        return "index";
    }

    @PostMapping("/products")
    public String upload(@RequestParam(name = "photos", required = false) List<MultipartFile> photos,
                         @RequestHeader(name = "HX-Request", required = false) String htmx,
                         Model model,
                         HttpServletResponse response) {
        // A browser sends one empty "photos" part when no file is chosen.
        List<MultipartFile> chosen = photos == null ? List.of()
                : photos.stream().filter(f -> !f.isEmpty() || (f.getOriginalFilename() != null && !f.getOriginalFilename().isBlank())).toList();
        ProductResponse product;
        try {
            product = service.create(chosen);
        } catch (InvalidUploadException | UploadLimitException e) {
            model.addAttribute("error", e.getMessage());
            if (htmx != null) {
                // htmx doesn't swap 4xx answers by default: the message is sent as a normal fragment.
                return "fragments :: error";
            }
            response.setStatus(e instanceof UploadLimitException
                    ? HttpStatus.TOO_MANY_REQUESTS.value() : HttpStatus.BAD_REQUEST.value());
            model.addAttribute("recent", service.list(0, RECENT).items());
            return "index";
        }
        if (htmx != null) {
            response.setHeader("HX-Push-Url", "/products/" + product.id());
            model.addAttribute("p", product);
            return "product :: result";
        }
        return "redirect:/products/" + product.id();
    }

    @GetMapping("/products/{id}")
    public String product(@PathVariable UUID id, Model model) {
        model.addAttribute("p", service.get(id));
        return "product";
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(Model model) {
        model.addAttribute("error", "This product doesn't exist (or was deleted).");
        model.addAttribute("recent", service.list(0, RECENT).items());
        return "index";
    }
}
