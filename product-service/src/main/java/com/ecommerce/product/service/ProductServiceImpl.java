package com.ecommerce.product.service;

import com.ecommerce.product.dto.ProductDto;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor // for auto generate constructor with all final fields
public class ProductServiceImpl implements ProductService{

    private final ProductRepository productRepo;
    private final ProductMapper productMapper;
    private final String uploadDir = System.getProperty("user.dir")+"/uploads/products/";

    /**
     * Creates product with optional category; category passed to preserve relation.
     **/
    @Override
    public ProductDto createProduct(ProductDto dto) {
        Category category=null;
        if(dto.getCategoryId() != null) {
            category = new Category();
            category.setId(dto.getCategoryId());
        }
        Product product =  productMapper.toEntity(dto, category); // category passed here because relation is not break
        Product saved =  productRepo.save(product);
        return productMapper.toDto(saved);
    }

    /**
     * Updates product fields; sets category if categoryId provided.
     **/
    @Override
    public ProductDto updateProduct(Long id, ProductDto dto) {
        Product product = productRepo
                .findById(id)
                .orElseThrow(() -> new RuntimeException("Product is not Found"));
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setDiscountPrice(dto.getDiscountPrice());
        product.setQuantity(dto.getQuantity());
        product.setBrand(dto.getBrand());
        product.setImageUrl(dto.getImageUrl());
        if (dto.getCategoryId() != null) {
            Category category = new Category();
            category.setId(dto.getCategoryId());
            product.setCategory(category);
        }
        return productMapper.toDto(productRepo.save(product));
    }

    /**
     * Deletes product by ID.
     **/
    @Override
    public void deleteProduct(Long id) {
        productRepo.deleteById(id);
    }

    /**
     * Returns product by ID; throws if not found.
     **/
    @Override
    public ProductDto getProductById(Long id) {
        Product p = productRepo
                .findById(id)
                .orElseThrow(()
                        -> new RuntimeException("product not found"));
        return productMapper.toDto(p);
    }

    /**
     * Paginated list with configurable sort.
     **/
    @Override
    public Page<ProductDto> getAllProduct(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc")? Sort.by(sortBy).ascending(): Sort.by(sortBy).descending();
        Pageable pageable =
                PageRequest.of(page, size, sort); // create pagination object
        Page<Product> productPage =  productRepo.findAll(pageable);
        return productPage.map(productMapper::toDto);
    }

    /**
     * Searches by keyword in name/description.
     **/
    @Override
    public Page<ProductDto> searchProduct(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> productPage =  productRepo.searchProducts(keyword, pageable);
        return productPage.map(productMapper::toDto);
    }

    /**
     * Filters by category and price range; delegates to advanceFilter.
     **/
    @Override
    public Page<ProductDto> filterProducts(Long categoryId, Double minPrice, Double maxPrice, int page, int size) {
        Double min = minPrice != null ? minPrice : 0.0;
        Double max = maxPrice != null ? maxPrice : Double.MAX_VALUE;
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> productPage =
                productRepo.advanceFilter(null, categoryId, min, max, pageable);
        return productPage.map(productMapper::toDto);
    }

    /**
     * Combined filter: keyword, category, price range, sort.
     **/
    @Override
    public Page<ProductDto> advanceFilter(String keyword, Long categoryId, Double minPrice, Double maxPrice, int page,
                                          int size, String sortBy, String sortDir) {
        Double min = minPrice != null ? minPrice : 0.0;
        Double max = maxPrice != null ? maxPrice : Double.MAX_VALUE;
        Sort sort = sortDir != null && sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy != null ? sortBy : "id").descending()
                : Sort.by(sortBy != null ? sortBy : "id").ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Product> productPage = productRepo.advanceFilter(keyword, categoryId, min, max, pageable);
        return productPage.map(productMapper::toDto);
    }

    /**
     * Validates file (size, type, extension), saves to upload dir,
     * updates product imageUrl.
     **/
    @Override
    public ProductDto uploadImage(Long productId, MultipartFile file) throws IOException {
        Product product =  productRepo.findById(productId).orElseThrow(()-> new RuntimeException("Product not found"));
        if(file.isEmpty()) {
            throw new RuntimeException("Image file is empty");
        }
        long maxSize = 2*1024*1024;
        if(file.getSize() > maxSize) {
            throw new RuntimeException("File size must be less then 2MB");
        }
        List<String> allowedType = List.of("image/jpeg","image/png","image/jpg");
        if(!allowedType.contains(file.getContentType())) {
            throw new RuntimeException("Only jpeg, png and jpg are allowed");
        }
        String originalName =  file.getOriginalFilename();
        if(originalName == null || !originalName.contains(".")) {
            throw new RuntimeException("Invalid file name");

        }
        String ext = originalName.substring(originalName.lastIndexOf(".")+1).toLowerCase();
        List<String> allowedExt = List.of("jpg","png","jpeg");
        if(!allowedExt.contains(ext)) {
            throw new RuntimeException("invalid image extensionj");
        }
        File folder = new File(uploadDir);
        if(!folder.exists()) {
            folder.mkdirs();
        }
        String fileName = UUID.randomUUID().toString()+"."+ext;
        Path filePath =  Paths.get(uploadDir+fileName);
        Files.write(filePath, file.getBytes());
        String imageUrl = "/products/images/"+fileName;
        product.setImageUrl(imageUrl);
        Product saved =  productRepo.save(product);
        return productMapper.toDto(product);
    }

}