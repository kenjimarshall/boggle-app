import cv2
import numpy as np
from scipy.ndimage import rotate
import argparse
import pytesseract
import pandas as pd
from intervaltree import IntervalTree, Interval


def preprocess_image(img, verbose=0):
    img_greyscale = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    img_greyscale_filtered = cv2.GaussianBlur(img_greyscale, (5, 5), 0)
    clean = cv2.adaptiveThreshold(img_greyscale_filtered, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                  cv2.THRESH_BINARY_INV, 51, 3)

    kernel_1 = cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3))
    kernel_2 = cv2.getStructuringElement(cv2.MORPH_CROSS, (3, 3))
    # lighting can cause some black dots within letters
    clean = cv2.dilate(clean, kernel_1, iterations=1)
    clean = cv2.morphologyEx(clean, cv2.MORPH_CLOSE, kernel_2, iterations=1)
    clean = cv2.erode(clean, kernel_1, iterations=1)
    # dilating and closing holes then eroding again

    if verbose >= 3:
        cv2.imshow("Processed Image", clean)
        cv2.waitKey()

    return clean


def fit_to_grid(img, verbose=0):

    h, w = img.shape[:2]
    contours, hierarchy = cv2.findContours(
        img, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)  # cv2.RETR_LIST ensures we get children in contour hierarchy

    letter_contours = []
    img_to_draw = img.copy()
    interval_tree_y = IntervalTree()
    interval_tree_x = IntervalTree()

    for contour in contours:  # Draw on bounding boxes
        # get rectangle bounding contour
        [x, y, w_c, h_c] = cv2.boundingRect(contour)

        # Don't plot small false positives that aren't text
        # In a cropped Boggle board, letters occupy between about 1/5 - 1/12 of a board dimension
        # if w_c < (w/12) or h_c < (h/12) or w_c > (w/5) or h_c > (h/5):
        #     continue

        # draw rectangle around contour on original image for visualization
        cv2.rectangle(img_to_draw, (x, y),
                      (x + w_c, y + h_c), (255, 0, 255), 2)

        letter_contour = img[y:y+h_c, x:x+w_c]  # extract image region
        letter_contours.append(letter_contour)

        # we keep x in datum to sort with later on
        interval_y = Interval(y, y+h_c, [letter_contour, x])
        interval_x = Interval(x, x+w_c, [letter_contour, y])
        interval_tree_y.add(interval_y)
        interval_tree_x.add(interval_x)

    # write original image with added contours to disk
    if verbose >= 1:
        cv2.imshow('Segmented Image', img_to_draw)
        cv2.waitKey()

    # merge all overlapping intervals together
    interval_tree_y.merge_overlaps(lambda a, b: list(a) + list(b))
    interval_tree_x.merge_overlaps(lambda a, b: list(a) + list(b))

    if len(interval_tree_y) != 4 or len(interval_tree_x) != 4:
        # identification failed to form necessary grid structure
        raise ValueError("Boggle grid could not be localized")

    # then we presume we found the grid
    grid = np.zeros((4, 4))
    sorted_letter_contours = []

    # go in intervals from low to high y-coordinate (top to bottom of image)
    # get the x intervals in order
    sorted_x = sorted(interval_tree_x, key=lambda x: x.begin)
    for i, int_y, in enumerate(sorted(interval_tree_y, key=lambda x: x.begin)):
        # i gives index of row we're working on
        y_contours = int_y.data[::2]  # contours at even indices
        y_pos = int_y.data[1::2]  # x-coordinates at odd indices
        # sorts by pos (x-coordinate)
        sorted_int_y = sorted(zip(y_pos, y_contours))
        if verbose >= 2:
            print(f"Y Interval: {int_y.begin} - {int_y.end}")

        for dat in sorted_int_y:  # iterate through each data point in row i
            if verbose >= 2:
                print(f"X position of contour: {dat[0]}")
            for j, int_x in enumerate(sorted_x):  # each of the 4 x-intervals
                if verbose >= 2:
                    print(
                        f"Comparing against x-interval: {int_x.begin} - {int_x.end}")
                if int_x.overlaps(dat[0]):  # then this contour is at [i, j]
                    if verbose >= 2:
                        print("MATCH")
                    if grid[i, j] == 1:  # a contour has already been found here
                        if verbose >= 2:
                            # ignore this contour
                            print("Grid position occupied. Ignoring.")
                    else:
                        if verbose >= 2:
                            print(f"Populating grid location [{i}, {j}].")
                        grid[i, j] = 1
                        sorted_letter_contours.append(dat[1])

                    break
    if verbose >= 1:
        print(f"Final Grid Population: {grid}")

    grid_vec = grid.ravel()
    missing_ix = np.where(grid_vec == 0)[0]
    for ix in missing_ix:
        # insert black image in missing spots
        sorted_letter_contours.insert(ix, np.zeros((100, 100)))

    return sorted_letter_contours


def reduce_to_size(img, size_to_reach=(800, 800), verbose=0):
    res = img.shape[:2]
    while np.any(np.greater(res, size_to_reach)):  # keep reducing size

        retain = 90  # 90 % retained at each step
        width = int(res[1] * retain / 100)
        height = int(res[0] * retain / 100)
        img = cv2.resize(
            img, (width, height), interpolation=cv2.INTER_AREA)
        if verbose >= 2:
            print(f"Image resized to {width} x {height}")
        res = img.shape[:2]

    return img


def contours_to_characters(contours, verbose=0):
    characters = []
    for contour in contours:
        h, w = contour.shape
        contour = cv2.copyMakeBorder(
            contour, 100, 100, 100, 100, cv2.BORDER_CONSTANT, value=[0, 0, 0])

        if w > h:
            contour_up = rotate(contour, 90)
        else:
            contour_up = contour

        contour_down = rotate(contour_up, 180)

        config = "--psm 10 -c tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        text_up = pytesseract.image_to_data(
            contour_up, config=config, output_type='data.frame')
        text_down = pytesseract.image_to_data(
            contour_down, config=config, output_type='data.frame')

        up_pred = text_up.iloc[-1]['text']  # prediction
        up_conf = text_up.iloc[-1]['conf']  # confidence
        down_pred = text_down.iloc[-1]['text']
        down_conf = text_down.iloc[-1]['conf']

        if verbose >= 1:
            print(f"Upward Orientation: {up_pred} (Confidence: {up_conf})")
            print(
                f"Downard Orientation: {down_pred} (Confidence: {down_conf})")

        if up_pred == " " and down_pred == " ":
            if verbose >= 1:
                print("Defaulting to empty prediction")
            pred = " "  # default prediction
        elif up_pred == " ":
            if verbose >= 1:
                print(
                    f"Upward orientation gave no result. Defaulting to downward prediction: {down_pred}")
            pred = down_pred
        elif down_pred == " ":
            if verbose >= 1:
                print(
                    f"Downward orientation gave no result. Defaulting to upward prediction: {up_pred}")
            pred = up_pred

        else:
            if up_conf >= down_conf:
                if verbose >= 1:
                    print(
                        f"Upward orientation more probable. Predicting {up_pred}")
                pred = up_pred
            else:
                if verbose >= 1:
                    print(
                        f"Downward orientation more probable. Predicting {down_pred}")
                pred = down_pred

        characters.append(pred.lower())

        if verbose >= 1:
            cv2.imshow('Upward Orientation of Letter', contour_up)
            cv2.waitKey()

    return characters


def read_image(img_path, verbose=0):
    img = cv2.imread(img_path)

    if verbose >= 3:
        cv2.imshow("Loaded Image", img)
        cv2.waitKey()

    return img


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("-i", "--image",
                    help="Image to process", required=True)
    ap.add_argument("-v", "--verbose",
                    help="Amount to display while executing", type=int, default=0)
    args = vars(ap.parse_args())
    verbose = args['verbose']
    img_path = args['image']
    img = read_image(img_path, verbose)
    img = reduce_to_size(img, (600, 600), verbose)

    img = preprocess_image(img, verbose)
    contours = fit_to_grid(img, verbose)

    character_predictions = contours_to_characters(contours, verbose)
    print(character_predictions)
