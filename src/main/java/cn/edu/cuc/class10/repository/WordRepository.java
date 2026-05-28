package cn.edu.cuc.class10.repository;

import cn.edu.cuc.class10.entity.Word;
import cn.edu.cuc.class10.entity.WordType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WordRepository extends JpaRepository<Word, String> {

    Optional<Word> findByContent(String content);

    boolean existsByContent(String content);

    List<Word> findByWordType(WordType wordType);

    @Query("SELECT w FROM Word w WHERE LOWER(w.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(w.translation) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Word> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT w FROM Word w WHERE w.partOfSpeech = :partOfSpeech")
    List<Word> findByPartOfSpeech(@Param("partOfSpeech") String partOfSpeech);

    @Query("SELECT w FROM Word w WHERE w.wordType = :wordType ORDER BY w.content ASC")
    List<Word> findByWordTypeOrderByContent(@Param("wordType") WordType wordType);

    long countByWordType(WordType wordType);

    List<Word> findByContentContainingIgnoreCase(String content);

    @Query("SELECT w FROM Word w ORDER BY w.content ASC")
    List<Word> findAllOrderByContentAsc();
}
