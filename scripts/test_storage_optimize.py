import importlib.util
import io
from pathlib import Path
import unittest
from PIL import Image

spec = importlib.util.spec_from_file_location("optimizer", Path(__file__).with_name("storage-optimize.py"))
optimizer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(optimizer)


class LosslessTests(unittest.TestCase):
    def test_transparent_pixels_and_dimensions_survive(self):
        image = Image.new("RGBA", (256, 256))
        image.putdata([(x, y, (x + y) % 256, (x * 3 + y) % 256) for y in range(256) for x in range(256)])
        source = io.BytesIO()
        image.save(source, "PNG", compress_level=0)
        result = optimizer.compress(source.getvalue())
        self.assertIsNotNone(result)
        with Image.open(io.BytesIO(result)) as restored:
            self.assertEqual(restored.size, image.size)
            self.assertEqual(restored.convert("RGBA").tobytes(), image.tobytes())

    def test_invalid_or_truncated_images_stay_untouched(self):
        self.assertIsNone(optimizer.compress(b"not actually a PNG"))
        self.assertIsNone(optimizer.compress(b"\x89PNG\r\n\x1a\n"))

    def test_animated_png_is_not_flattened(self):
        first = Image.new("RGBA", (100, 100), "red")
        second = Image.new("RGBA", (100, 100), "blue")
        output = io.BytesIO()
        first.save(output, "PNG", save_all=True, append_images=[second], duration=100)
        self.assertIsNone(optimizer.compress(output.getvalue()))


if __name__ == "__main__":
    unittest.main()
